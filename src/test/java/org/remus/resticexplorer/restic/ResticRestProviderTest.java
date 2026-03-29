package org.remus.resticexplorer.restic;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResticRestProviderTest {

    private final ResticRestProvider provider = new ResticRestProvider();

    @Test
    void testGetType() {
        assertEquals("REST", provider.getType());
    }

    @Test
    void testBuildEnvironmentWithoutCredentials() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals(1, env.size());
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildEnvironmentWithCredentials() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "admin");
        repo.setProperty(RepositoryPropertyKey.REST_PASSWORD, "restpass");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals(3, env.size());
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
        assertEquals("admin", env.get("RESTIC_REST_USERNAME"));
        assertEquals("restpass", env.get("RESTIC_REST_PASSWORD"));
    }

    @Test
    void testBuildEnvironmentWithUsernameOnly() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "admin");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals(2, env.size());
        assertEquals("admin", env.get("RESTIC_REST_USERNAME"));
    }

    @Test
    void testBuildRepositoryUrlIsAlwaysReturnedAsIs() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "admin");
        repo.setProperty(RepositoryPropertyKey.REST_PASSWORD, "restpass");

        // Credentials go via env vars, URL is never modified
        assertEquals("rest:http://localhost:8000/", provider.buildRepositoryUrl(repo));
    }

    @Test
    void testBuildExtraArgumentsReturnsEmpty() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");

        assertTrue(provider.buildExtraArguments(repo).isEmpty());
    }
}
