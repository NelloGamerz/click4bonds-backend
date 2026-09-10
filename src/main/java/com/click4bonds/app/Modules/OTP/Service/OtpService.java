package com.click4bonds.app.Modules.OTP.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.Common.Redis.RedisService;
import com.click4bonds.app.Modules.OTP.Config.OtpProperties;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpMaxAttemptsExceededException;
import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;
import com.click4bonds.app.Modules.OTP.Generator.OtpGenerator;
import com.click4bonds.app.Modules.OTP.Model.OtpData;
import com.click4bonds.app.Modules.OTP.Model.OtpType;

import lombok.extern.slf4j.Slf4j;

/**
 * Issues and verifies one-time passwords.
 *
 * <p>The service is deliberately self-contained: it generates a code, stores
 * its hash in Redis and verifies submissions. It never delivers anything —
 * the caller receives the plaintext code and decides how to send it. There is
 * no dependency on email, SMS, authentication or any controller.</p>
 *
 * <p>State lives entirely in Redis, under a key that includes the channel,
 * so an email OTP and an SMS OTP for the same person coexist and can never be
 * substituted for one another. Expiry is the Redis key TTL — no scheduled
 * cleanup runs anywhere.</p>
 */
@Slf4j
@Service
public class OtpService {

    private static final Pattern DIGITS = Pattern.compile("^[0-9]+$");

    private final RedisService redisService;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final OtpProperties properties;

    public OtpService(
            RedisService redisService,
            OtpGenerator otpGenerator,
            OtpHasher otpHasher,
            OtpProperties properties) {

        this.redisService = redisService;
        this.otpGenerator = otpGenerator;
        this.otpHasher = otpHasher;
        this.properties = properties;
    }

    /**
     * Generates a new OTP and stores its state in Redis.
     *
     * <p>Generating again for the same type + identifier replaces the pending
     * code immediately and restarts its full TTL — the previous code stops
     * working the moment the new one exists. A request made before the resend
     * cooldown elapses is rejected and leaves the pending code untouched.</p>
     *
     * @param type       channel the OTP is for
     * @param identifier email address or phone number
     * @return the plaintext OTP; the caller is responsible for delivering it
     * @throws BadRequestException         when the type or identifier is invalid
     * @throws OtpResendCooldownException  when the cooldown is still active
     */
    public String generateOtp(OtpType type, String identifier) {

        String normalized = IdentifierNormalizer.normalize(type, identifier);

        acquireResendCooldown(type, normalized);

        String otp = otpGenerator.generate();

        Instant now = Instant.now();
        Duration ttl = Duration.ofMinutes(properties.getExpiryMinutes());

        OtpData data = new OtpData(
                otpHasher.hash(otp),
                normalized,
                type,
                now,
                now.plus(ttl),
                0);

        redisService.set(OtpKeyFactory.otpKey(type, normalized), data, ttl);

        log.info("OTP generated (type={}, ttlSeconds={})", type, ttl.toSeconds());

        return otp;
    }

    /**
     * Verifies a submitted OTP.
     *
     * <p>On success the code is consumed: it is deleted, so replaying the
     * same value fails. On failure the attempt counter is incremented; once
     * the configured maximum is reached the code is destroyed and the caller
     * must request a new one.</p>
     *
     * @throws BadRequestException              when the input is malformed
     * @throws InvalidOtpException              when the code is wrong, unknown or expired
     * @throws OtpMaxAttemptsExceededException  when the attempt limit is hit
     */
    public void verifyOtp(OtpType type, String identifier, String otp) {

        String normalized = IdentifierNormalizer.normalize(type, identifier);
        validateOtpFormat(otp);

        String key = OtpKeyFactory.otpKey(type, normalized);

        OtpData data = redisService.get(key, OtpData.class)
                .orElseThrow(() -> {
                    log.info("OTP verification failed (type={}, reason=missing)", type);
                    return new InvalidOtpException();
                });

        // Redis expires the key on its own; this guards a read that raced the TTL.
        if (isExpired(data)) {
            redisService.delete(key);
            log.info("OTP verification failed (type={}, reason=expired)", type);
            throw new InvalidOtpException();
        }

        if (data.getAttemptCount() >= properties.getMaxAttempts()) {
            redisService.delete(key);
            log.warn("OTP verification blocked (type={}, reason=attempts-exhausted)", type);
            throw new OtpMaxAttemptsExceededException();
        }

        if (otpHasher.matches(otp, data.getOtpHash())) {
            // Single use: the code dies with the verification that consumed it.
            redisService.delete(key);
            log.info("OTP verification succeeded (type={})", type);
            return;
        }

        registerFailedAttempt(key, data, type);
    }

    /**
     * Records a failed attempt, or destroys the code when it was the last
     * one allowed.
     *
     * <p>The rewrite carries the <em>remaining</em> TTL across, so a wrong
     * guess never extends the life of the code it failed to match. That
     * remaining window is read back from Redis rather than recomputed from
     * {@link OtpData#getExpiresAt()}: Redis owns expiry, and the application
     * clock has no business second-guessing it.</p>
     */
    private void registerFailedAttempt(String key, OtpData data, OtpType type) {

        int attempts = data.getAttemptCount() + 1;

        log.info("OTP verification failed (type={}, attempt={})", type, attempts);

        if (attempts >= properties.getMaxAttempts()) {
            redisService.delete(key);
            log.warn("OTP verification blocked (type={}, reason=attempts-exhausted)", type);
            throw new OtpMaxAttemptsExceededException();
        }

        long remainingSeconds = redisService.getTtl(key);

        // The key expired between the read and this rewrite: nothing to count.
        if (remainingSeconds <= 0) {
            redisService.delete(key);
            throw new InvalidOtpException();
        }

        redisService.set(
                key,
                data.withAttemptCount(attempts),
                Duration.ofSeconds(remainingSeconds));

        throw new InvalidOtpException();
    }

    /**
     * Rejects malformed submissions before they reach Redis.
     *
     * <p>A code of the wrong shape can never match a stored one, so this
     * short-circuit cannot be used to skip attempt accounting for a value
     * that had a chance of being right.</p>
     */
    private void validateOtpFormat(String otp) {

        if (otp == null || otp.isBlank()) {
            throw new BadRequestException("OTP is required");
        }

        if (!DIGITS.matcher(otp).matches()) {
            throw new BadRequestException("OTP must contain digits only");
        }

        if (otp.length() != properties.getLength()) {
            throw new BadRequestException(
                    "OTP must be exactly " + properties.getLength() + " digits");
        }
    }

    private boolean isExpired(OtpData data) {
        return Instant.now().isAfter(data.getExpiresAt());
    }

    /**
     * Claims the resend slot for this type + identifier.
     *
     * <p>{@code setIfAbsent} makes the check atomic, so two concurrent
     * requests cannot both mint a code — no distributed lock required.</p>
     */
    private void acquireResendCooldown(OtpType type, String normalized) {

        String cooldownKey = OtpKeyFactory.cooldownKey(type, normalized);
        Duration cooldown = Duration.ofSeconds(properties.getResendCooldownSeconds());

        boolean acquired = redisService.setIfAbsent(cooldownKey, Boolean.TRUE, cooldown);

        if (!acquired) {
            log.info("OTP request rejected (type={}, reason=cooldown)", type);
            throw new OtpResendCooldownException();
        }
    }
}
