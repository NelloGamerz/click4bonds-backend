package com.click4bonds.app.Modules.Email.Template;

/**
 * Account creation / welcome template.
 *
 * <p>The copy stays factual: it welcomes the user, confirms the account and
 * points at the platform. It makes no claim about returns, approvals or
 * regulatory status.</p>
 */
public final class WelcomeEmailTemplate {

    public static final String SUBJECT = "Welcome to Click4Bond";

    private static final String BODY = """
            <h1 style="margin:0 0 16px 0;font-size:22px;line-height:30px;color:#0b3b7a;">Welcome to Click4Bond, {{userName}}</h1>
            <p style="margin:0 0 16px 0;">Your Click4Bond account has been created successfully and your email address is verified.</p>
            <p style="margin:0 0 16px 0;">You can now sign in to explore bonds and other fixed-income opportunities, review issue details and track your holdings in one place.</p>
            {{cta}}
            <p style="margin:0 0 16px 0;color:#5b6b7c;font-size:13px;line-height:20px;">Investments in bonds and other securities are subject to market risks. Please read all related documents carefully before investing.</p>
            <p style="margin:0;color:#5b6b7c;font-size:13px;line-height:20px;">If you did not create this account, please contact our support team so we can look into it.</p>
            """;

    private static final String CTA = """
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 24px 0;">
              <tr>
                <td align="center" bgcolor="#0b3b7a" style="border-radius:6px;">
                  <a href="{{loginUrl}}" style="display:inline-block;padding:13px 28px;font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:bold;color:#ffffff;text-decoration:none;">Sign in to Click4Bond</a>
                </td>
              </tr>
            </table>
            <p style="margin:0 0 24px 0;color:#5b6b7c;font-size:12px;line-height:18px;">If the button does not work, copy this link into your browser: <span style="color:#0b3b7a;word-break:break-all;">{{loginUrl}}</span></p>
            """;

    private WelcomeEmailTemplate() {
    }

    /**
     * @param userName name to greet the user with; a neutral greeting is used
     *                 when it is missing
     * @param loginUrl application URL for the call to action; the button is
     *                 omitted when the project has no such URL configured
     * @return the complete HTML document
     */
    public static String render(String userName, String loginUrl) {

        String greeting = (userName == null || userName.isBlank()) ? "there" : userName.trim();

        String cta = (loginUrl == null || loginUrl.isBlank())
                ? ""
                : CTA.replace("{{loginUrl}}", loginUrl);

        String content = BODY
                .replace("{{userName}}", greeting)
                .replace("{{cta}}", cta);

        return EmailLayout.render("Welcome to Click4Bond", content);
    }
}
