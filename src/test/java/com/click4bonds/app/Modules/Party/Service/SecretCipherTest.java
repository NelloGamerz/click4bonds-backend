package com.click4bonds.app.Modules.Party.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("click4bonds-test-party-key-32by!".getBytes(StandardCharsets.UTF_8));

    private final SecretCipher cipher = new SecretCipher(KEY);

    @Test
    void aValueRoundTrips() {

        String plain = "AAAPZ1234C";

        assertEquals(plain, cipher.decrypt(cipher.encrypt(plain)));
    }

    @Test
    void ciphertextRevealsNothingAndChangesEveryTime() {

        String plain = "123456789012";

        String first = cipher.encrypt(plain);
        String second = cipher.encrypt(plain);

        assertNotEquals(plain, first);
        assertTrue(first.contains("=") || first.length() > plain.length());
        // A fresh IV per call, so equal plaintext never produces equal ciphertext.
        assertNotEquals(first, second);
    }

    @Test
    void aTamperedValueIsRefused() {

        byte[] sealed = Base64.getDecoder().decode(cipher.encrypt("AAAPZ1234C"));
        sealed[sealed.length - 1] ^= 0x01;

        String tampered = Base64.getEncoder().encodeToString(sealed);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }
}
