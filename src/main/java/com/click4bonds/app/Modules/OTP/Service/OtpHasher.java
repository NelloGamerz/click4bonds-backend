package com.click4bonds.app.Modules.OTP.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.OTP.Config.OtpProperties;

/**
 * Hashes OTP codes before they reach Redis.
 *
 * <p>A bare digest (SHA-256) would not be enough here: an OTP only has
 * {@code 10^length} possible values, so anyone holding the Redis snapshot
 * could recover the code by brute force in milliseconds. The digest is
 * therefore keyed — HMAC-SHA256 with a secret that lives only in the
 * environment — which makes an offline attack on the store useless without
 * that secret.</p>
 *
 * <p>Comparison is constant time, so verification does not leak the expected
 * hash through timing.</p>
 */
@Component
public class OtpHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public OtpHasher(OtpProperties properties) {

        String secret = properties.getHashSecret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "otp.hash-secret must be configured (set the OTP_HASH_SECRET environment variable)");
        }

        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * @return base64 encoding of {@code HMAC-SHA256(secret, otp)}
     */
    public String hash(String otp) {

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);

            byte[] digest = mac.doFinal(otp.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(digest);

        } catch (Exception ex) {
            // The key and the code are both excluded from the message.
            throw new IllegalStateException("Could not hash OTP", ex);
        }
    }

    /**
     * @return {@code true} when {@code otp} hashes to {@code expectedHash}
     */
    public boolean matches(String otp, String expectedHash) {

        if (otp == null || expectedHash == null) {
            return false;
        }

        byte[] actual = hash(otp).getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(actual, expected);
    }
}
