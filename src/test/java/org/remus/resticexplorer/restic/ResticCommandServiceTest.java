package org.remus.resticexplorer.restic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.remus.resticexplorer.config.exception.ResticCommandException;
import org.remus.resticexplorer.config.exception.ResticCommandTimeoutException;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ResticCommandService} that verify the exact restic CLI arguments are
 * built, in particular that the correct {@code --mode} flag is passed for repository-level vs
 * per-snapshot statistics.
 *
 * <p>These tests do not require a real restic binary or repository: a tiny fake {@code restic}
 * executable is generated on disk. It records every argument it is invoked with and prints
 * JSON on stdout. The fake is configured to report a different size per counting mode so the
 * tests can assert both the argument order and the JSON parsing in one place.
 */
class ResticCommandServiceTest {

    @TempDir
    Path tempDir;

    private ResticCommandService service;
    private Path argsFile;

    @BeforeEach
    void setUp() throws IOException {
        argsFile = tempDir.resolve("restic_args.log");
        Path fakeRestic = tempDir.resolve("restic");
        // The fake restic script: append its arguments (one per line) to the log file,
        // then print JSON on stdout. The JSON depends on the counting mode that was requested,
        // mirroring the real restic `stats` command.
        String script = "#!/bin/sh\n"
                + "printf '%s\\n' \"$@\" >> " + argsFile + "\n"
                + "for a in \"$@\"; do\n"
                + "  if [ \"$a\" = \"raw-data\" ]; then\n"
                + "    printf '%s' '{\"mode\":\"raw-data\",\"total_size\":1000,\"total_blob_count\":50}'\n"
                + "    exit 0\n"
                + "  fi\n"
                + "  if [ \"$a\" = \"files-by-contents\" ]; then\n"
                + "    printf '%s' '{\"mode\":\"files-by-contents\",\"total_size\":900,\"total_file_count\":42}'\n"
                + "    exit 0\n"
                + "  fi\n"
                + "  if [ \"$a\" = \"restore-size\" ]; then\n"
                + "    printf '%s' '{\"mode\":\"restore-size\",\"total_size\":5000,\"total_file_count\":42}'\n"
                + "    exit 0\n"
                + "  fi\n"
                + "done\n"
                + "# default (no explicit mode): restic reports restore-size by default\n"
                + "printf '%s' '{\"mode\":\"restore-size\",\"total_size\":5000,\"total_file_count\":42}'\n";
        Files.writeString(fakeRestic, script, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(fakeRestic,
                PosixFilePermissions.fromString("rwxr-xr-x"));

        ResticRepositoryProvider provider = new ResticS3Provider();
        service = new ResticCommandService(List.of(provider), new ObjectMapper());
        injectBinary(service, fakeRestic.toString());
        injectTimeout(service, 30);
    }

    @Test
    void getStats_withRawDataMode_passesModeFlagAndParsesOnDiskSize() throws Exception {
        ResticRepository repo = s3Repo();

        Map<String, Object> stats = service.getStats(repo, "raw-data");

        // The size parsed must be the raw-data value, proving the correct mode reached restic.
        assertThat(asLong(stats, "total_size")).isEqualTo(1000L);
        assertThat(asLong(stats, "total_blob_count")).isEqualTo(50L);
        // raw-data does not report total_file_count
        assertThat(stats).doesNotContainKey("total_file_count");

        List<String> args = readArgs();
        // Mode flag must appear and come before --json; command must be `stats`.
        assertThat(args).contains("stats");
        assertThat(args).contains("--mode");
        int modeIdx = args.indexOf("--mode");
        int jsonIdx = args.indexOf("--json");
        assertThat(modeIdx).isLessThan(jsonIdx);
        assertThat(args.get(modeIdx + 1)).isEqualTo("raw-data");
        // --no-lock must be present (avoids locking the repo during a read)
        assertThat(args).contains("--no-lock");
    }

    @Test
    void getStats_withRestoreSizeMode_passesModeFlagAndParsesLogicalSize() throws Exception {
        ResticRepository repo = s3Repo();

        Map<String, Object> stats = service.getStats(repo, "restore-size");

        assertThat(asLong(stats, "total_size")).isEqualTo(5000L);
        assertThat(asLong(stats, "total_file_count")).isEqualTo(42L);

        List<String> args = readArgs();
        int modeIdx = args.indexOf("--mode");
        assertThat(args.get(modeIdx + 1)).isEqualTo("restore-size");
    }

    @Test
    void getSnapshotStats_passesSnapshotIdBeforeModeFlag() throws Exception {
        ResticRepository repo = s3Repo();

        Map<String, Object> stats = service.getSnapshotStats(repo, "abc123", "restore-size");

        assertThat(asLong(stats, "total_size")).isEqualTo(5000L);

        List<String> args = readArgs();
        // The snapshot id must be a positional argument to `stats` and appear before the --mode flag.
        int statsIdx = args.indexOf("stats");
        int idIdx = args.indexOf("abc123");
        int modeIdx = args.indexOf("--mode");
        assertThat(idIdx).isGreaterThan(statsIdx);
        assertThat(idIdx).isLessThan(modeIdx);
        assertThat(args.get(modeIdx + 1)).isEqualTo("restore-size");
        assertThat(args).contains("--json");
    }

    @Test
    void getStats_nonZeroExitCode_throwsResticCommandException() throws Exception {
        // Replace the fake with one that always fails.
        Path failing = tempDir.resolve("restic");
        Files.writeString(failing, """
                #!/bin/sh
                printf 'boom\\n' 1>&2
                exit 12
                """, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(failing, PosixFilePermissions.fromString("rwxr-xr-x"));
        injectBinary(service, failing.toString());

        ResticRepository repo = s3Repo();

        assertThatThrownBy(() -> service.getStats(repo, "raw-data"))
                .isInstanceOf(ResticCommandException.class);
    }

    @Test
    void getStats_blankOutput_returnsEmptyMap() throws Exception {
        // Replace the fake with one that prints nothing.
        Path empty = tempDir.resolve("restic");
        Files.writeString(empty, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(empty, PosixFilePermissions.fromString("rwxr-xr-x"));
        injectBinary(service, empty.toString());

        ResticRepository repo = s3Repo();

        Map<String, Object> stats = service.getStats(repo, "raw-data");

        assertThat(stats).isEmpty();
    }

    /**
     * Regression test for the SFTP ssh-process leak: a {@code restic} process that hangs while
     * holding stdout open AND has forked a long-lived grandchild (standing in for the {@code ssh}
     * process the SFTP backend spawns). The old code blocked in {@code readAll()} until EOF, so the
     * timeout never fired and the grandchild was orphaned to PID 1. This asserts that the timeout
     * now fires and the whole process tree (restic + ssh stand-in) is reaped.
     */
    @Test
    void getStats_hangsAndSpawnsGrandchild_reapsWholeProcessTreeOnTimeout() throws Exception {
        Path pidFile = tempDir.resolve("grandchild.pid");
        // Fork a long-lived grandchild (the ssh stand-in) then hang with stdout left open, like a
        // stuck SFTP connection. The grandchild's PID is recorded so the test can confirm it is
        // reaped rather than orphaned.
        Path hanging = tempDir.resolve("restic");
        Files.writeString(hanging,
                "#!/bin/sh\n"
                        + "( exec sleep 300 ) &\n"
                        + "echo \"$!\" > " + pidFile + "\n"
                        + "exec sleep 300\n",
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(hanging, PosixFilePermissions.fromString("rwxr-xr-x"));
        injectBinary(service, hanging.toString());
        injectTimeout(service, 2);

        ResticRepository repo = s3Repo();

        long start = System.nanoTime();
        assertThatThrownBy(() -> service.getStats(repo, "raw-data"))
                .isInstanceOf(ResticCommandTimeoutException.class);
        // The timeout must actually fire -- i.e. not be blocked behind a blocking read.
        double elapsedSeconds = (System.nanoTime() - start) / 1e9;
        assertThat(elapsedSeconds).isLessThan(15);

        // The forked grandchild (the "ssh" process) must be reaped by the process-tree kill.
        assertThat(Files.exists(pidFile)).isTrue();
        long grandchildPid = Long.parseLong(
                Files.readString(pidFile, StandardCharsets.UTF_8).trim());
        long deadline = System.currentTimeMillis() + 3000;
        while (pidAlive(grandchildPid) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(pidAlive(grandchildPid))
                .as("grandchild (ssh stand-in) pid %d must be reaped, not orphaned to PID 1", grandchildPid)
                .isFalse();
    }

    /** True while a process with the given pid is still present in the process table. */
    private static boolean pidAlive(long pid) {
        return ProcessHandle.allProcesses().anyMatch(p -> p.pid() == pid);
    }

    private ResticRepository s3Repo() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");
        repo.setRepositoryPassword("secret");
        return repo;
    }

    /**
     * Reads a numeric field as a long, mirroring how {@link ResticCommandService} callers cast
     * the JSON value. Jackson 3 deserializes small JSON integers as {@link Integer}, so a
     * direct {@code isEqualTo(longValue)} assertion would fail on the type difference.
     */
    private static long asLong(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).longValue();
    }

    private List<String> readArgs() throws IOException {
        return Files.readAllLines(argsFile, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
    }

    // Reflectively set the @Value-injected fields that are normally bound by the Spring container.
    private void injectBinary(ResticCommandService target, String value) throws IOException {
        setField(target, "resticBinary", value);
    }

    private void injectTimeout(ResticCommandService target, int value) throws IOException {
        setField(target, "timeoutSeconds", value);
    }

    private void setField(Object target, String name, Object value) throws IOException {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IOException("Failed to inject field " + name, e);
        }
    }
}
