package com.click4bonds.app.Modules.Party.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Authenticated encryption for the party financial fields (PAN, account
 * number).
 *
 * <p>AES-256-GCM, with a fresh 12-byte IV per value. The stored form is base64
 * of {@code IV || ciphertext || tag}; nothing else is stored, and the key lives
 * only in configuration. GCM is chosen over CBC because it detects tampering:
 * a value edited in the database fails to decrypt rather than decrypting to
 * something plausible.</p>
 *
 * <p>The key is read from {@code party.encryption-key} and must be 32 random
 * bytes, base64-encoded. It has no functional default in production; see
 * {@code application-prod.yaml}. Changing it makes existing ciphertext
 * unreadable, so it is key material to be rotated by re-encryption, not by
 * editing the property.</p>
 */
@Component
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LENGTH = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${party.encryption-key:}") String base64Key) {

        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "party.encryption-key is required: generate 32 random bytes and base64-encode them");
        }

        byte[] decoded;

        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("party.encryption-key is not valid base64", ex);
        }

        if (decoded.length != KEY_LENGTH) {
            throw new IllegalStateException(
                    "party.encryption-key must decode to " + KEY_LENGTH + " bytes");
        }

        this.key = new SecretKeySpec(decoded, ALGORITHM);
    }

    /**
     * @param plaintext value to protect, never null
     * @return base64 of IV + ciphertext + tag
     */
    public String encrypt(String plaintext) {

        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(sealed, 0, combined, iv.length, sealed.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt party secret", ex);
        }
    }

    /**
     * @param stored base64 of IV + ciphertext + tag
     * @return the plaintext
     * @throws IllegalStateException when the value is malformed, was tampered
     *         with, or was written under a different key
     */
    public String decrypt(String stored) {

        if (stored == null) {
            return null;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(stored);

            if (combined.length <= IV_LENGTH) {
                throw new IllegalStateException("Stored party secret is too short");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] sealed = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, sealed, 0, sealed.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);

        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt party secret", ex);
        }
    }
}
