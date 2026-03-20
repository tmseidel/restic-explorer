package org.remus.resticexplorer.restic;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ResticS3ProviderTest {

    private final ResticS3Provider provider = new ResticS3Provider();

    @Test
    void testGetType() {
        assertEquals("S3", provider.getType());
    }

    @Test
    void testBuildEnvironment() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");
        repo.setRepositoryPassword("secret");
        repo.setProperty(RepositoryPropertyKey.S3_ACCESS_KEY, "AKIAIOSFODNN7EXAMPLE");
        repo.setProperty(RepositoryPropertyKey.S3_SECRET_KEY, "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        repo.setProperty(RepositoryPropertyKey.S3_REGION, "us-east-1");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertEquals("AKIAIOSFODNN7EXAMPLE", env.get("AWS_ACCESS_KEY_ID"));
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", env.get("AWS_SECRET_ACCESS_KEY"));
        assertEquals("us-east-1", env.get("AWS_DEFAULT_REGION"));
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildEnvironmentWithoutOptionalFields() {
        ResticRepository repo = new ResticRepository();
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");
        repo.setRepositoryPassword("secret");

        Map<String, String> env = provider.buildEnvironment(repo);

        assertFalse(env.containsKey("AWS_ACCESS_KEY_ID"));
        assertFalse(env.containsKey("AWS_SECRET_ACCESS_KEY"));
        assertFalse(env.containsKey("AWS_DEFAULT_REGION"));
        assertEquals("secret", env.get("RESTIC_PASSWORD"));
    }

    @Test
    void testBuildRepositoryUrl() {
        ResticRepository repo = new ResticRepository();
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");

        assertEquals("s3:https://s3.amazonaws.com/test-bucket/restic", provider.buildRepositoryUrl(repo));
    }
}
