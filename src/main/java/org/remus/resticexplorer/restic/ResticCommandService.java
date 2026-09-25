package org.remus.resticexplorer.restic;

import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.config.exception.ProviderNotFoundException;
import org.remus.resticexplorer.config.exception.ResticCommandException;
import org.remus.resticexplorer.config.exception.ResticCommandTimeoutException;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ResticCommandService {

    private final Map<String, ResticRepositoryProvider> providers;
    private final ObjectMapper objectMapper;

    @Value("${restic.binary:restic}")
    private String resticBinary;

    @Value("${restic.timeout:300}")
    private int timeoutSeconds;

    public ResticCommandService(List<ResticRepositoryProvider> providerList, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.providers = new HashMap<>();
        for (ResticRepositoryProvider provider : providerList) {
            providers.put(provider.getType(), provider);
        }
    }

    public List<Map<String, Object>> listSnapshots(ResticRepository repository) {
        String output = executeCommand(repository, "--no-lock", "snapshots", "--json");
        if (output.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(output, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ResticCommandException("Failed to parse snapshots response: " + e.getMessage(), e);
        }
    }

    /**
     * Repository-level statistics.
     *
     * @param mode counting mode: {@code restore-size} (default, logical size of all snapshots),
     *             {@code raw-data} (actual blob size on disk), {@code files-by-contents}, or
     *             {@code blobs-per-file}
     */
    public Map<String, Object> getStats(ResticRepository repository, String mode) {
        String output = executeCommand(repository, "--no-lock", "stats", "--mode", mode, "--json");
        if (output.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(output, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ResticCommandException("Failed to parse stats response: " + e.getMessage(), e);
        }
    }

    /**
     * Per-snapshot statistics.
     *
     * @param mode counting mode: {@code restore-size} (default, logical size of the snapshot),
     *             {@code raw-data}, {@code files-by-contents}, or {@code blobs-per-file}
     */
    public Map<String, Object> getSnapshotStats(ResticRepository repository, String snapshotId, String mode) {
        String output = executeCommand(repository, "--no-lock", "stats", snapshotId, "--mode", mode, "--json");
        if (output.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(output, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse stats for snapshot {}: {}", snapshotId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public String checkRepository(ResticRepository repository) {
        return executeCommand(repository, "--no-lock", "check", "--read-data");
    }

    public List<String> listLocks(ResticRepository repository) {
        String output = executeCommand(repository, "--no-lock", "list", "locks");
        if (output.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(output.trim().split("\n"))
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
    }

    public void unlockRepository(ResticRepository repository) {
        executeCommand(repository, "unlock");
    }

    public InputStream downloadSnapshot(ResticRepository repository, String snapshotId) {
        ResticRepositoryProvider provider = getProvider(repository);
        Map<String, String> env = provider.buildEnvironment(repository);
        String repoUrl = provider.buildRepositoryUrl(repository);

        List<String> command = new ArrayList<>();
        command.add(resticBinary);
        command.add("-r");
        command.add(repoUrl);
        command.addAll(provider.buildExtraArguments(repository));
        command.add("--no-lock");
        command.add("dump");
        command.add(snapshotId);
        command.add("/");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();
            return process.getInputStream();
        } catch (Exception e) {
            throw new ResticCommandException("Failed to start restic dump: " + e.getMessage(), e);
        }
    }

    private String executeCommand(ResticRepository repository, String... args) {
        ResticRepositoryProvider provider = getProvider(repository);
        Map<String, String> env = provider.buildEnvironment(repository);
        String repoUrl = provider.buildRepositoryUrl(repository);

        List<String> command = new ArrayList<>();
        command.add(resticBinary);
        command.add("-r");
        command.add(repoUrl);
        command.addAll(provider.buildExtraArguments(repository));
        command.addAll(Arrays.asList(args));

        log.debug("Executing restic command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            // Drain stdout and stderr on background threads. If we read inline (as the old
            // code did), readAll() blocks until EOF and the timeout below is never reached: a
            // hung restic/ssh process keeps its stream open, so the read blocks forever and the
            // process is never reaped. Draining on side threads lets waitFor() act as the real
            // gate, and the tree-kill below reaps the ssh grandchild that a plain
            // destroyForcibly() would miss.
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread outThread = drainTo(process.getInputStream(), out);
            Thread errThread = drainTo(process.getErrorStream(), err);
            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                killProcessTree(process);
                outThread.join(2000);
                errThread.join(2000);
                throw new ResticCommandTimeoutException(timeoutSeconds);
            }

            outThread.join(2000);
            errThread.join(2000);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // Make sure the process tree (restic + any ssh grandchild) is fully reaped
                // before the caller moves on, so a failed scan never leaks an idle ssh session.
                killProcessTree(process);

                // Log full details for operators/diagnostics.
                log.error("Restic command failed (exit code {}), stderr: {}", exitCode, err);

                // Build a sanitized, user-facing message without exposing raw stderr.
                String stderr = err.toString();
                String message;
                if (stderr.contains("unsupported repository version")) {
                    // Use a stable message key that can be localized/resolved in the UI layer.
                    message = "error.restic.unsupportedRepoVersion";
                } else {
                    message = "Restic command failed (exit code " + exitCode + ")";
                }

                throw new ResticCommandException(message, stderr);
            }

            return out.toString();
        } catch (Exception e) {
            if (e instanceof ResticCommandException rce) {
                throw rce;
            }
            throw new ResticCommandException("Failed to execute restic command: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a stream to EOF into {@code sink} on a daemon thread, so the calling thread is never
     * blocked waiting for the process to close its output. Swallows IO errors (the process dying
     * mid-read is the normal case).
     */
    private Thread drainTo(InputStream in, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                sink.append(readAll(reader));
            } catch (IOException ignored) {
                // Process terminated before the stream was fully read; nothing to salvage.
            }
        });
        t.setDaemon(true);
        return t;
    }

    /**
     * Forcefully terminates the process <em>and</em> every descendant (leaf-first, so the {@code
     * ssh} grandchild restic spawned is reaped before restic itself). A plain
     * {@link Process#destroyForcibly()} only signals the direct child; the SFTP backend's ssh
     * process is a grandchild and would otherwise be orphaned to PID 1, holding an open network
     * session indefinitely.
     */
    private void killProcessTree(Process process) {
        process.toHandle().descendants()
                .sorted(Comparator.reverseOrder())
                .forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private ResticRepositoryProvider getProvider(ResticRepository repository) {
        String type = repository.getType().name();
        ResticRepositoryProvider provider = providers.get(type);
        if (provider == null) {
            throw new ProviderNotFoundException(type);
        }
        return provider;
    }

    private String readAll(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
