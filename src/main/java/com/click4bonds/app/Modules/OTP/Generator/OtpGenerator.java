package com.click4bonds.app.Modules.OTP.Generator;

/**
 * Produces OTP codes.
 *
 * <p>Kept as an interface so the code-generation strategy can be swapped
 * (and stubbed in tests) without touching {@code OtpService}.</p>
 */
public interface OtpGenerator {

    /**
     * @return a freshly generated, numeric OTP of the configured length,
     *         including any leading zeroes
     */
    String generate();
}
