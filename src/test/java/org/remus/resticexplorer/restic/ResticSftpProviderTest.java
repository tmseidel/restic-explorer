package org.remus.resticexplorer.restic;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
    void testBuildEnvironmentWithSftpCommand() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.SFTP);
        repo.setUrl("sftp:user@host:/srv/restic-repo");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.SFTP_COMMAND, "ssh user@host -i /path/to/key -s sftp");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals("secret", env.get("RESTIC_PASSWORD"));
        assertEquals("ssh user@host -i /path/to/key -s sftp", env.get("RESTIC_SFTP_COMMAND"));
    }

    @Test
    void testBuildEnvironmentWithAllOptions() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.SFTP);
        repo.setUrl("sftp:user@host:/srv/restic-repo");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.SFTP_PASSWORD_COMMAND, "cat /path/to/password-file");
        repo.setProperty(RepositoryPropertyKey.SFTP_COMMAND, "ssh user@host -i /path/to/key -s sftp");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals("cat /path/to/password-file", env.get("RESTIC_PASSWORD_COMMAND"));
        assertFalse(env.containsKey("RESTIC_PASSWORD"));
        assertEquals("ssh user@host -i /path/to/key -s sftp", env.get("RESTIC_SFTP_COMMAND"));
    }

    @Test
    void testBuildRepositoryUrl() {
        ResticRepository repo = new ResticRepository();
        repo.setUrl("sftp:user@host:/srv/restic-repo");

        assertEquals("sftp:user@host:/srv/restic-repo", provider.buildRepositoryUrl(repo));
    }
}
