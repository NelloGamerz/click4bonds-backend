package com.click4bonds.app.Modules.Email.Template;

/**
 * One-time-code (email verification) template.
 *
 * <p>Rendering is plain placeholder substitution into a static HTML document —
 * the project has no template engine, and the dynamic values are trivial, so
 * no additional dependency is introduced.</p>
 */
public final class OtpEmailTemplate {

    public static final String SUBJECT = "Verify your Click4Bond account";

    /**
     * Validity of a code when the caller does not specify its own.
     *
     * <p>Kept in step with the OTP module's default ({@code otp.expiry-minutes},
     * 15 minutes). It is only a fallback: code that issues an OTP should pass
     * the configured value explicitly, so the message can never promise a
     * window the code does not actually have.</p>
     */
    public static final int DEFAULT_EXPIRY_MINUTES = 15;

    private static final String BODY = """
            <h1 style="margin:0 0 16px 0;font-size:22px;line-height:30px;color:#0b3b7a;">Email verification</h1>
            <p style="margin:0 0 16px 0;">Hi,</p>
            <p style="margin:0 0 24px 0;">Use the verification code below to confirm your email address and continue with your Click4Bond account.</p>
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td align="center" style="background-color:#f0f3f7;border:1px solid #d9e0e8;border-radius:8px;padding:20px;">
                  <span style="font-family:'Courier New',Courier,monospace;font-size:32px;font-weight:bold;letter-spacing:8px;color:#0b3b7a;">{{otp}}</span>
                </td>
              </tr>
            </table>
            <p style="margin:24px 0 16px 0;">Enter this code on the verification screen. The code expires in <strong>{{expiryMinutes}} minutes</strong>.</p>
            <p style="margin:0 0 16px 0;color:#5b6b7c;font-size:13px;line-height:20px;">For your security, never share this code with anyone. Click4Bond will never ask you for it over the phone or by email.</p>
            <p style="margin:0;color:#5b6b7c;font-size:13px;line-height:20px;">If you did not request this code, you can safely ignore this email.</p>
            """;

    private OtpEmailTemplate() {
    }

    /**
     * @param otp           the one-time code, shown prominently in the message
     * @param expiryMinutes number of minutes the code stays valid
     * @return the complete HTML document
     */
    public static String render(String otp, int expiryMinutes) {

        if (otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("OTP must not be blank");
        }

        String content = BODY
                .replace("{{otp}}", otp)
                .replace("{{expiryMinutes}}", String.valueOf(expiryMinutes));

        return EmailLayout.render("Your Click4Bond verification code", content);
    }

    /**
     * @param otp the one-time code
     * @return the complete HTML document using {@link #DEFAULT_EXPIRY_MINUTES}
     */
    public static String render(String otp) {
        return render(otp, DEFAULT_EXPIRY_MINUTES);
    }
}
