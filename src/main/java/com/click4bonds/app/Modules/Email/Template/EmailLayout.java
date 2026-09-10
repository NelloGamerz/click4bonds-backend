package com.click4bonds.app.Modules.Email.Template;

/**
 * Shared Click4Bond HTML shell for transactional emails.
 *
 * <p>The markup is intentionally boring: table based layout, inline styles
 * only, no JavaScript and no external stylesheet, which is what email clients
 * (including Gmail, Outlook and mobile clients) render reliably. Templates
 * supply their own body HTML, injected at the {@code {{content}}} placeholder;
 * the inbox preview snippet is taken from {@code {{preheader}}}.</p>
 */
final class EmailLayout {

    private static final String SHELL = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="x-apple-disable-message-reformatting">
              <title>Click4Bond</title>
            </head>
            <body style="margin:0;padding:0;background-color:#f4f6f9;">
              <div style="display:none;font-size:1px;line-height:1px;color:#f4f6f9;max-height:0;max-width:0;opacity:0;overflow:hidden;">{{preheader}}</div>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6f9;">
                <tr>
                  <td align="center" style="padding:24px 12px;">
                    <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:600px;background-color:#ffffff;border:1px solid #e3e8ee;border-radius:8px;">
                      <tr>
                        <td style="background-color:#0b3b7a;border-radius:8px 8px 0 0;padding:22px 32px;font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:bold;color:#ffffff;">Click4<span style="color:#d4af37;">Bond</span></td>
                      </tr>
                      <tr>
                        <td style="padding:32px;font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:24px;color:#1f2933;">{{content}}</td>
                      </tr>
                      <tr>
                        <td style="background-color:#f0f3f7;border-radius:0 0 8px 8px;padding:22px 32px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:19px;color:#5b6b7c;">
                          <p style="margin:0 0 8px 0;">Click4Bond &mdash; a platform for exploring bonds and other fixed-income investment opportunities.</p>
                          <p style="margin:0 0 8px 0;">This is an automated message, please do not reply to this email.</p>
                          <p style="margin:0;">&copy; Click4Bond. All rights reserved.</p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

    private EmailLayout() {
    }

    /**
     * @param preheader short inbox preview text
     * @param content   body HTML of the message
     * @return the complete HTML document
     */
    static String render(String preheader, String content) {
        return SHELL
                .replace("{{preheader}}", preheader)
                .replace("{{content}}", content);
    }
}
