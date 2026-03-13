package org.remus.resticexplorer.config.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for sensitive data at rest.
 * Each encryption produces a unique ciphertext due to random IV.
 */
@Component
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${restic.encryption.key:}")
    private String encryptionKeyBase64;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            log.warn("No encryption key configured (restic.encryption.key). "
                    + "Sensitive data will be stored in plain text. "
                    + "Generate a key with: openssl rand -base64 32");
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                    "Invalid encryption key length: " + keyBytes.length
                    + " bytes. Must be 16, 24, or 32 bytes (128, 192, or 256 bit). "
                    + "Generate with: openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("Encryption enabled for sensitive data at rest (AES-{}-GCM)", keyBytes.length * 8);
    }

    /**
     * Returns true if encryption is configured and active.
     */
    public boolean isEnabled() {
        return secretKey != null;
    }

    /**
     * Encrypt a plaintext string. Returns Base64-encoded ciphertext (IV + encrypted data).
     * Returns the original value if encryption is not configured.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (!isEnabled()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: [IV (12 bytes)][ciphertext + tag]
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt a Base64-encoded ciphertext. Returns the original plaintext.
     * Returns the original value if encryption is not configured.
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        if (!isEnabled()) {
            return ciphertext;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Not Base64-encoded — likely unencrypted legacy data
            log.debug("Value does not appear to be encrypted, returning as-is");
            return ciphertext;
        } catch (Exception e) {
            // Decryption failed — could be unencrypted legacy data
            log.debug("Decryption failed, returning value as-is (may be unencrypted legacy data)");
            return ciphertext;
        }
    }
}
