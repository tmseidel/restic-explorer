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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
        String output = executeCommand(repository, "snapshots", "--json");
        if (output == null || output.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(output, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ResticCommandException("Failed to parse snapshots response: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getStats(ResticRepository repository) {
        String output = executeCommand(repository, "stats", "--json");
        if (output == null || output.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(output, new TypeReference<>() {});
        } catch (Exception e) {
            throw new ResticCommandException("Failed to parse stats response: " + e.getMessage(), e);
        }
    }

    public String checkRepository(ResticRepository repository) {
        return executeCommand(repository, "check", "--read-data");
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

            String stdout;
            String stderr;
            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                stdout = readAll(stdoutReader);
                stderr = readAll(stderrReader);
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ResticCommandTimeoutException(timeoutSeconds);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // Log full details for operators/diagnostics.
                log.error("Restic command failed (exit code {}), stderr: {}", exitCode, stderr);

                // Build a sanitized, user-facing message without exposing raw stderr.
                String message;
                if (stderr.contains("unsupported repository version")) {
                    // Use a stable message key that can be localized/resolved in the UI layer.
                    message = "error.restic.unsupportedRepoVersion";
                } else {
                    message = "Restic command failed (exit code " + exitCode + ")";
                }

                throw new ResticCommandException(message);
            }

            return stdout;
        } catch (Exception e) {
            if (e instanceof ResticCommandException rce) {
                throw rce;
            }
            throw new ResticCommandException("Failed to execute restic command: " + e.getMessage(), e);
        }
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
