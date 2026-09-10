package com.click4bonds.app.Modules.User.Dto;

/**
 * Outcome of a verification request.
 *
 * <p>Carries a human readable message and nothing else: never the issued code,
 * its hash, or anything describing the Redis entry behind it. The code exists
 * only long enough to be handed to {@code EmailService}.</p>
 */
public record VerificationResponse(String message) {
}
