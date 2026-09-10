package com.click4bonds.app.Modules.OTP.Service;

import java.util.concurrent.atomic.AtomicInteger;

import com.click4bonds.app.Modules.Common.Redis.InMemoryRedisService;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Generator.OtpGenerator;

/**
 * Shared wiring for the OTP service tests: default properties, a Redis stub
 * and generator stubs whose output the test can predict.
 */
final class OtpTestSupport {

    static final String EMAIL = "user@example.com";
    static final String PHONE = "+919876543210";

    private OtpTestSupport() {
    }

    static OtpProperties defaultProperties() {

        OtpProperties properties = new OtpProperties();
        properties.setLength(6);
        properties.setExpiryMinutes(15);
        properties.setMaxAttempts(5);
        properties.setResendCooldownSeconds(60);
        properties.setHashSecret("test-only-otp-hash-secret");

        return properties;
    }

    static OtpService service(
            InMemoryRedisService redis,
            OtpGenerator generator,
            OtpProperties properties) {

        return new OtpService(redis, generator, new OtpHasher(properties), properties);
    }

    /** Returns 100000, 100001, ... so successive codes are distinct and valid. */
    static OtpGenerator sequentialGenerator() {

        AtomicInteger sequence = new AtomicInteger(100_000);

        return () -> String.valueOf(sequence.getAndIncrement());
    }

    static OtpGenerator fixedGenerator(String otp) {
        return () -> otp;
    }
}
