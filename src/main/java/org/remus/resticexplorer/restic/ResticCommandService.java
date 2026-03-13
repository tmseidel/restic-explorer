package org.remus.resticexplorer.restic;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
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

    public List<Map<String, Object>> listSnapshots(ResticRepository repository) throws Exception {
        String output = executeCommand(repository, "snapshots", "--json");
        if (output == null || output.isBlank()) {
            return Collections.emptyList();
        }
        return objectMapper.readValue(output, new TypeReference<>() {});
    }

    public Map<String, Object> getStats(ResticRepository repository) throws Exception {
        String output = executeCommand(repository, "stats", "--json");
        if (output == null || output.isBlank()) {
            return Collections.emptyMap();
        }
        return objectMapper.readValue(output, new TypeReference<>() {});
    }

    public InputStream downloadSnapshot(ResticRepository repository, String snapshotId) throws Exception {
        ResticRepositoryProvider provider = getProvider(repository);
        Map<String, String> env = provider.buildEnvironment(repository);
        String repoUrl = provider.buildRepositoryUrl(repository);

        ProcessBuilder pb = new ProcessBuilder(
                resticBinary, "-r", repoUrl, "dump", snapshotId, "/"
        );
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);

        Process process = pb.start();
        return process.getInputStream();
    }

    private String executeCommand(ResticRepository repository, String... args) throws Exception {
        ResticRepositoryProvider provider = getProvider(repository);
        Map<String, String> env = provider.buildEnvironment(repository);
        String repoUrl = provider.buildRepositoryUrl(repository);

        List<String> command = new ArrayList<>();
        command.add(resticBinary);
        command.add("-r");
        command.add(repoUrl);
        command.addAll(Arrays.asList(args));

        log.debug("Executing restic command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);

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
            throw new RuntimeException("Restic command timed out after " + timeoutSeconds + " seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Restic command failed (exit code " + exitCode + "): " + stderr);
        }

        return stdout;
    }

    private ResticRepositoryProvider getProvider(ResticRepository repository) {
        String type = repository.getType().name();
        ResticRepositoryProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("No provider found for repository type: " + type);
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
