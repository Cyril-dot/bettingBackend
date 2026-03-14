package com.bettingPlatform.BettingWebsite.Config.Security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class TokenEncryptionService {

    // AES-GCM parameters
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private final SecretKey encryptionKey;

    public TokenEncryptionService(@Value("${security.token.encryption-key}") String encryptionKeyHex) {
        // Convert hex string to SecretKey
        this.encryptionKey = hexToSecretKey(encryptionKeyHex);
        log.info("✅ Token encryption service initialized");
    }

    /**
     * Encrypt a JWT token
     * Returns: Base64(IV + encrypted_token + auth_tag)
     */
    public String encryptToken(String plainToken) {
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            // Encrypt
            byte[] encryptedBytes = cipher.doFinal(plainToken.getBytes("UTF-8"));

            // Combine: IV + encrypted_data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            // Encode to Base64
            String encryptedToken = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(byteBuffer.array());

            log.debug("🔒 Token encrypted successfully");
            return encryptedToken;

        } catch (Exception e) {
            log.error("❌ Token encryption failed", e);
            throw new RuntimeException("Failed to encrypt token", e);
        }
    }

    /**
     * Decrypt an encrypted JWT token
     */
    public String decryptToken(String encryptedToken) {
        try {
            // Decode from Base64
            byte[] encryptedBytes = Base64.getUrlDecoder().decode(encryptedToken);

            // Extract IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(cipherText);
            String plainToken = new String(decryptedBytes, "UTF-8");

            log.debug("🔓 Token decrypted successfully");
            return plainToken;

        } catch (Exception e) {
            log.error("❌ Token decryption failed", e);
            throw new RuntimeException("Invalid or corrupted token", e);
        }
    }

    /**
     * Convert hex string to SecretKey
     */
    private SecretKey hexToSecretKey(String hexKey) {
        try {
            byte[] keyBytes = hexStringToByteArray(hexKey);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.error("❌ Failed to parse encryption key", e);
            throw new RuntimeException("Invalid encryption key format", e);
        }
    }

    /**
     * Helper: Convert hex string to byte array
     */
    private byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Utility method to generate a new encryption key (run once, store in config)
     */
    public static String generateEncryptionKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256); // AES-256
        SecretKey secretKey = keyGenerator.generateKey();
        
        byte[] keyBytes = secretKey.getEncoded();
        StringBuilder hexString = new StringBuilder();
        for (byte b : keyBytes) {
            hexString.append(String.format("%02x", b));
        }
        
        return hexString.toString();
    }

    // Main method to generate key (run once)
    public static void main(String[] args) throws Exception {
        String key = generateEncryptionKey();
        System.out.println("🔑 Generated AES-256 Encryption Key (save this in application.properties):");
        System.out.println(key);
    }
}