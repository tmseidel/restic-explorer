package org.remus.resticexplorer.restic;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResticAzureProviderTest {

    private final ResticAzureProvider provider = new ResticAzureProvider();

    @Test
    void testGetType() {
        assertEquals("AZURE", provider.getType());
    }

    @Test
    void testBuildEnvironment() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.AZURE);
        repo.setUrl("azure:backups:/test_1");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.AZURE_ACCOUNT_NAME, "mystorageaccount");
        repo.setProperty(RepositoryPropertyKey.AZURE_ACCOUNT_KEY, "base64encodedkey==");
        repo.setProperty(RepositoryPropertyKey.AZURE_ENDPOINT_SUFFIX, "core.windows.net");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals("mystorageaccount", env.get("AZURE_ACCOUNT_NAME"));
        assertEquals("base64encodedkey==", env.get("AZURE_ACCOUNT_KEY"));
        assertEquals("core.windows.net", env.get("AZURE_ENDPOINT_SUFFIX"));
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildEnvironmentWithoutOptionalFields() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.AZURE);
        repo.setUrl("azure:backups:/test_1");
        repo.setRepositoryPassword("secret");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertFalse(env.containsKey("AZURE_ACCOUNT_NAME"));
        assertFalse(env.containsKey("AZURE_ACCOUNT_KEY"));
        assertFalse(env.containsKey("AZURE_ENDPOINT_SUFFIX"));
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildEnvironmentWithoutEndpointSuffix() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.AZURE);
        repo.setUrl("azure:backups:/test_1");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.AZURE_ACCOUNT_NAME, "mystorageaccount");
        repo.setProperty(RepositoryPropertyKey.AZURE_ACCOUNT_KEY, "base64encodedkey==");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals("mystorageaccount", env.get("AZURE_ACCOUNT_NAME"));
        assertEquals("base64encodedkey==", env.get("AZURE_ACCOUNT_KEY"));
        assertFalse(env.containsKey("AZURE_ENDPOINT_SUFFIX"));
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildRepositoryUrl() {
        ResticRepository repo = new ResticRepository();
        repo.setUrl("azure:backups:/test_1");

        assertEquals("azure:backups:/test_1", provider.buildRepositoryUrl(repo));
    }
}
