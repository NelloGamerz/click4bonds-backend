package com.click4bonds.app.Modules.Email.Template;

/**
 * Deal confirmation letter email.
 *
 * <p>Sent once a created deal's confirmation document has been rendered and
 * stored, so the customer receives the letter without signing in. The document
 * itself travels as an attachment; the body only identifies the deal it
 * belongs to and tells the customer what to do with it.</p>
 *
 * <p>Every value substituted into the body is escaped first. The customer's
 * name and the deal reference are not attacker-controlled in practice, but
 * they are assembled from user data, and an email body is still HTML.</p>
 */
public final class DealConfirmationEmailTemplate {

    public static final String SUBJECT = "Your Click4Bond deal confirmation";

    private static final String BODY = """
            <h1 style="margin:0 0 16px 0;font-size:22px;line-height:30px;color:#0b3b7a;">Deal confirmation</h1>
            <p style="margin:0 0 16px 0;">Hi {{customerName}},</p>
            <p style="margin:0 0 16px 0;">Thank you for your investment through Click4Bond. The confirmation letter for your deal <strong>{{dealReference}}</strong> is attached to this email.</p>
            <p style="margin:0 0 16px 0;">Please keep this document for your records. If anything in it does not match your understanding of the deal, contact our support team before the settlement date.</p>
            <p style="margin:0;color:#5b6b7c;font-size:13px;line-height:20px;">Investments in bonds and other securities are subject to market risks. Please read all related documents carefully before investing.</p>
            """;

    private DealConfirmationEmailTemplate() {
    }

    /**
     * @param dealReference customer-facing deal reference, shown in the subject
     *                      so the message can be found without opening it
     * @return the subject line
     */
    public static String subject(String dealReference) {

        return (dealReference == null || dealReference.isBlank())
                ? SUBJECT
                : SUBJECT + " - " + dealReference;
    }

    /**
     * @param customerName  name used in the greeting; a neutral greeting is used
     *                      when it is missing
     * @param dealReference customer-facing deal reference
     * @return the complete HTML document
     */
    public static String render(String customerName, String dealReference) {

        String greeting = (customerName == null || customerName.isBlank())
                ? "there"
                : escape(customerName.trim());

        String content = BODY
                .replace("{{customerName}}", greeting)
                .replace("{{dealReference}}", escape(dealReference == null ? "" : dealReference));

        return EmailLayout.render("Your Click4Bond deal confirmation", content);
    }

    private static String escape(String value) {

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
