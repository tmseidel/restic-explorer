package org.remus.resticexplorer.config.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class EncryptionServiceTest {

    @Autowired
    private EncryptionService encryptionService;

    @Test
    void testEncryptionIsEnabled() {
        assertTrue(encryptionService.isEnabled());
    }

    @Test
    void testEncryptDecryptRoundTrip() {
        String plaintext = "my-secret-password-123";
        String encrypted = encryptionService.encrypt(plaintext);

        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted, "Encrypted value must differ from plaintext");

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted, "Decrypted value must match original plaintext");
    }

    @Test
    void testEncryptProducesDifferentCiphertextEachTime() {
        String plaintext = "same-password";
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        assertNotEquals(encrypted1, encrypted2,
                "Each encryption should produce unique ciphertext due to random IV");

        assertEquals(plaintext, encryptionService.decrypt(encrypted1));
        assertEquals(plaintext, encryptionService.decrypt(encrypted2));
    }

    @Test
    void testEncryptNull() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));
    }

    @Test
    void testDecryptLegacyUnencryptedData() {
        // Simulate reading legacy unencrypted data from DB
        String legacyPlaintext = "old-unencrypted-password";
        String result = encryptionService.decrypt(legacyPlaintext);
        assertEquals(legacyPlaintext, result,
                "Unencrypted legacy data should be returned as-is");
    }
}
