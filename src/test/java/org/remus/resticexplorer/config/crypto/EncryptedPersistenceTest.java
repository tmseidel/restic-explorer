package org.remus.resticexplorer.config.crypto;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EncryptedPersistenceTest {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void testRepositoryPasswordIsEncryptedInDatabase() {
        ResticRepository repo = new ResticRepository();
        repo.setName("Encrypted Test");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test/restic");
        repo.setRepositoryPassword("super-secret-password");
        repo.setScanIntervalMinutes(60);
        repo.setEnabled(true);

        ResticRepository saved = repositoryService.save(repo);
        entityManager.flush();
        entityManager.clear();

        // Read the raw value from DB using JDBC (bypasses JPA converter)
        String rawPassword = jdbcTemplate.queryForObject(
                "SELECT repository_password FROM restic_repositories WHERE id = ?",
                String.class, saved.getId());

        assertNotNull(rawPassword);
        assertNotEquals("super-secret-password", rawPassword,
                "Password must be encrypted in the database, not stored in plaintext");

        // Verify the entity still returns decrypted value
        ResticRepository found = repositoryService.findById(saved.getId()).orElseThrow();
        assertEquals("super-secret-password", found.getRepositoryPassword(),
                "Decrypted password must match the original");
    }

    @Test
    void testSensitivePropertiesAreEncryptedInDatabase() {
        ResticRepository repo = new ResticRepository();
        repo.setName("S3 Encrypted Props");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test/restic");
        repo.setRepositoryPassword("password");
        repo.setProperty(RepositoryPropertyKey.S3_ACCESS_KEY, "AKIAIOSFODNN7EXAMPLE");
        repo.setProperty(RepositoryPropertyKey.S3_SECRET_KEY, "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        repo.setProperty(RepositoryPropertyKey.S3_REGION, "us-east-1");
        repo.setScanIntervalMinutes(60);
        repo.setEnabled(true);

        ResticRepository saved = repositoryService.save(repo);
        entityManager.flush();
        entityManager.clear();

        // Read raw property values from DB
        String rawAccessKey = jdbcTemplate.queryForObject(
                "SELECT property_value FROM repository_properties WHERE repository_id = ? AND property_key = ?",
                String.class, saved.getId(), "S3_ACCESS_KEY");
        String rawSecretKey = jdbcTemplate.queryForObject(
                "SELECT property_value FROM repository_properties WHERE repository_id = ? AND property_key = ?",
                String.class, saved.getId(), "S3_SECRET_KEY");
        String rawRegion = jdbcTemplate.queryForObject(
                "SELECT property_value FROM repository_properties WHERE repository_id = ? AND property_key = ?",
                String.class, saved.getId(), "S3_REGION");

        // Sensitive properties must be encrypted
        assertNotEquals("AKIAIOSFODNN7EXAMPLE", rawAccessKey,
                "S3 Access Key must be encrypted in the database");
        assertNotEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", rawSecretKey,
                "S3 Secret Key must be encrypted in the database");

        // Non-sensitive properties should remain as-is
        assertEquals("us-east-1", rawRegion,
                "Non-sensitive S3 Region should not be encrypted");

        // Verify entity still returns decrypted values
        ResticRepository found = repositoryService.findById(saved.getId()).orElseThrow();
        assertEquals("AKIAIOSFODNN7EXAMPLE", found.getProperty(RepositoryPropertyKey.S3_ACCESS_KEY));
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", found.getProperty(RepositoryPropertyKey.S3_SECRET_KEY));
        assertEquals("us-east-1", found.getProperty(RepositoryPropertyKey.S3_REGION));
    }
}
