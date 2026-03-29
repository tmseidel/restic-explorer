package org.remus.resticexplorer.restic;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResticRcloneProviderTest {

    private final ResticRcloneProvider provider = new ResticRcloneProvider();

    @Test
    void testGetType() {
        assertEquals("RCLONE", provider.getType());
    }

    @Test
    void testBuildEnvironment() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.RCLONE);
        repo.setUrl("rclone:b2prod:yggdrasil");
        repo.setRepositoryPassword("secret");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals(1, env.size());
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildRepositoryUrl() {
        ResticRepository repo = new ResticRepository();
        repo.setUrl("rclone:b2prod:yggdrasil/foo/bar");

        assertEquals("rclone:b2prod:yggdrasil/foo/bar", provider.buildRepositoryUrl(repo));
    }

    @Test
    void testBuildExtraArgumentsEmpty() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.RCLONE);
        repo.setUrl("rclone:b2prod:yggdrasil");

        List<String> args = provider.buildExtraArguments(repo);

        assertTrue(args.isEmpty());
    }

    @Test
    void testBuildExtraArgumentsWithProgram() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.RCLONE);
        repo.setUrl("rclone:b2prod:yggdrasil");
        repo.setProperty(RepositoryPropertyKey.RCLONE_PROGRAM, "/usr/local/bin/rclone");

        List<String> args = provider.buildExtraArguments(repo);

        assertEquals(2, args.size());
        assertEquals("-o", args.get(0));
        assertEquals("rclone.program=/usr/local/bin/rclone", args.get(1));
    }

    @Test
    void testBuildExtraArgumentsWithArgs() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.RCLONE);
        repo.setUrl("rclone:b2prod:yggdrasil");
        repo.setProperty(RepositoryPropertyKey.RCLONE_ARGS, "serve restic --stdio --bwlimit 1M --b2-hard-delete --verbose");

        List<String> args = provider.buildExtraArguments(repo);

        assertEquals(2, args.size());
        assertEquals("-o", args.get(0));
        assertEquals("rclone.args=serve restic --stdio --bwlimit 1M --b2-hard-delete --verbose", args.get(1));
    }

    @Test
    void testBuildExtraArgumentsWithBothProgramAndArgs() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.RCLONE);
        repo.setUrl("rclone:b2prod:yggdrasil");
        repo.setProperty(RepositoryPropertyKey.RCLONE_PROGRAM, "/path/to/rclone");
        repo.setProperty(RepositoryPropertyKey.RCLONE_ARGS, "serve restic --stdio --b2-hard-delete");

        List<String> args = provider.buildExtraArguments(repo);

        assertEquals(4, args.size());
        assertEquals("-o", args.get(0));
        assertEquals("rclone.program=/path/to/rclone", args.get(1));
        assertEquals("-o", args.get(2));
        assertEquals("rclone.args=serve restic --stdio --b2-hard-delete", args.get(3));
    }
}
