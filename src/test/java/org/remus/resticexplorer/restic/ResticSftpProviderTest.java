package org.remus.resticexplorer.restic;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResticSftpProviderTest {

    private final ResticSftpProvider provider = new ResticSftpProvider();

    @Test
    void testGetType() {
        assertEquals("SFTP", provider.getType());
    }

    @Test
    void testBuildEnvironmentWithPassword() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.SFTP);
        repo.setUrl("sftp:user@host:/srv/restic-repo");
        repo.setRepositoryPassword("secret");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals("secret", env.get("RESTIC_PASSWORD"));
        assertFalse(env.containsKey("RESTIC_PASSWORD_COMMAND"));
        assertFalse(env.containsKey("RESTIC_SFTP_COMMAND"));
    }

    @Test
    void testBuildEnvironmentWithPasswordCommand() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.SFTP);
        repo.setUrl("sftp:user@host:/srv/restic-repo");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.SFTP_PASSWORD_COMMAND, "cat /path/to/password-file");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals("cat /path/to/password-file", env.get("RESTIC_PASSWORD_COMMAND"));
        assertFalse(env.containsKey("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildExtraArgumentsWithSftpCommand() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.SFTP);
        repo.setUrl("sftp:user@host:/srv/restic-repo");
        repo.setProperty(RepositoryPropertyKey.SFTP_COMMAND, "ssh user@host -i /path/to/key -s sftp");

        List<String> args = provider.buildExtraArguments(repo);

        assertEquals(2, args.size());
        assertEquals("-o", args.get(0));
        assertEquals("sftp.command=ssh user@host -i /path/to/key -s sftp", args.get(1));
    }

    @Test
    void testBuildExtraArgumentsWithoutSftpCommand() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.SFTP);
        repo.setUrl("sftp:user@host:/srv/restic-repo");

        List<String> args = provider.buildExtraArguments(repo);

        assertTrue(args.isEmpty());
    }

    @Test
    void testBuildRepositoryUrl() {
        ResticRepository repo = new ResticRepository();
        repo.setUrl("sftp:user@host:/srv/restic-repo");

        assertEquals("sftp:user@host:/srv/restic-repo", provider.buildRepositoryUrl(repo));
    }
}
