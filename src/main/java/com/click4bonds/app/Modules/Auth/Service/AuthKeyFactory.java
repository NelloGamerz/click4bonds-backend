package com.click4bonds.app.Modules.Auth.Service;

/**
 * Builds every Redis key the authentication module owns.
 *
 * <p>Key layout:</p>
 * <pre>
 * auth:session:{session-id}              the session itself
 * auth:session:grace:{old-id}            short-lived pointer from a rotated id
 * auth:otpratelimit:{client-address}     per-address OTP request counter
 * </pre>
 *
 * <p>Keys are namespaced under {@code auth:} so they can never collide with the
 * OTP module's own {@code otp:*} namespace. Nothing outside this class
 * assembles these keys by hand.</p>
 */
public final class AuthKeyFactory {

    private static final String ROOT = "auth";

    private static final String SESSION = "session";

    /**
     * Namespace for the post-rotation pointer. A separate segment rather than a
     * suffix on the session key, so a session identifier can never be crafted
     * to collide with a grace entry — the two live in different key spaces.
     */
    private static final String GRACE = "grace";

    private static final String OTP_RATE_LIMIT = "otpratelimit";

    private AuthKeyFactory() {
    }

    /** Key holding a live session, addressed by its own identifier. */
    public static String sessionKey(String sessionId) {
        return ROOT + ":" + SESSION + ":" + sessionId;
    }

    /**
     * Key pointing from a rotated-out session identifier to its replacement.
     *
     * @see AuthSessionService for why this exists
     */
    public static String graceKey(String previousSessionId) {
        return ROOT + ":" + SESSION + ":" + GRACE + ":" + previousSessionId;
    }

    /** Key holding the outstanding OTP request count for one client address. */
    public static String otpRateLimitKey(String clientAddress) {
        return ROOT + ":" + OTP_RATE_LIMIT + ":" + clientAddress;
    }
}
