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
    void testBuildEnvironment() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals(1, env.size());
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildRepositoryUrlWithCredentials() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "admin");
        repo.setProperty(RepositoryPropertyKey.REST_PASSWORD, "restpass");

        String url = provider.buildRepositoryUrl(repo);

        assertEquals("rest:http://admin:restpass@localhost:8000/", url);
    }

    @Test
    void testBuildRepositoryUrlWithCredentialsHttps() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:https://backup.example.com:8000/repo");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "user");
        repo.setProperty(RepositoryPropertyKey.REST_PASSWORD, "pass");

        String url = provider.buildRepositoryUrl(repo);

        assertEquals("rest:https://user:pass@backup.example.com:8000/repo", url);
    }

    @Test
    void testBuildRepositoryUrlWithoutCredentials() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");

        String url = provider.buildRepositoryUrl(repo);

        assertEquals("rest:http://localhost:8000/", url);
    }

    @Test
    void testBuildRepositoryUrlWithUsernameOnly() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "admin");

        String url = provider.buildRepositoryUrl(repo);

        // Without password, credentials are not injected
        assertEquals("rest:http://localhost:8000/", url);
    }

    @Test
    void testBuildExtraArgumentsReturnsEmpty() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");

        assertTrue(provider.buildExtraArguments(repo).isEmpty());
    }

    @Test
    void testBuildRepositoryUrlWithSpecialCharactersInCredentials() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:http://localhost:8000/");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "user@domain");
        repo.setProperty(RepositoryPropertyKey.REST_PASSWORD, "p@ss:word/test");

        String url = provider.buildRepositoryUrl(repo);

        assertEquals("rest:http://user%40domain:p%40ss%3Aword%2Ftest@localhost:8000/", url);
    }

    @Test
    void testBuildRepositoryUrlWithoutRestPrefix() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("http://localhost:8000/");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "admin");
        repo.setProperty(RepositoryPropertyKey.REST_PASSWORD, "pass");

        // Without rest: prefix, URL is returned as-is
        assertEquals("http://localhost:8000/", provider.buildRepositoryUrl(repo));
    }

    @Test
    void testBuildRepositoryUrlWithUnknownScheme() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.REST);
        repo.setUrl("rest:ftp://localhost:8000/");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.REST_USERNAME, "admin");
        repo.setProperty(RepositoryPropertyKey.REST_PASSWORD, "pass");

        // Unknown scheme after rest: prefix, URL is returned as-is
        assertEquals("rest:ftp://localhost:8000/", provider.buildRepositoryUrl(repo));
    }
}
