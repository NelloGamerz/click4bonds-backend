package com.click4bonds.app.Modules.Document.Config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration of the document module.
 *
 * <pre>
 * document:
 *   enabled: true
 *   storage:
 *     directory: ./storage/deal-confirmations
 *   template:
 *     path: deal_confirmation/ATSPL Deal Format.xlsx
 *     sheet-name: "PSU Private Sale "
 *   pdf:
 *     enabled: true
 *     soffice-path: soffice
 *   deal:
 *     face-value: 100
 *   organisation:
 *     pan: AAHCA7743E
 * </pre>
 *
 * <p>Split into engine settings, document content policy, and legal-entity
 * identity. The first is the same in every environment; the second is per-market
 * and changes with the business's arithmetic; the third is who we are, and
 * changes only if a second entity is onboarded.</p>
 *
 * <p>None of these are secrets. A PAN and an IFSC code identify an organisation;
 * they are not credentials. They therefore carry real defaults rather than being
 * required, because a blank PAN printed on a customer's confirmation letter is
 * worse than a visibly wrong one.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "document")
public class DocumentProperties {

    /**
     * Master switch for document generation.
     *
     * <p>{@code false} wires the no-op implementation, so deals are created and
     * simply produce no document. This is the switch for a machine with no
     * LibreOffice — a developer laptop or CI — where failure would otherwise be
     * guaranteed on every purchase.</p>
     */
    private boolean enabled = true;

    private Storage storage = new Storage();

    private Template template = new Template();

    private Pdf pdf = new Pdf();

    private Deal deal = new Deal();

    private Organisation organisation = new Organisation();

    /** Where generated documents are kept. */
    @Data
    public static class Storage {

        /**
         * Root directory for every generated document. Relative paths resolve
         * against the working directory.
         *
         * <p>On a container this must point at a mounted volume, or every
         * document is lost on redeploy while the database still claims one
         * exists.</p>
         */
        private String directory = "./storage/deal-confirmations";

        /**
         * Whether to keep the filled spreadsheet alongside the PDF.
         *
         * <p>Kept by default: the spreadsheet is what an operator needs to
         * correct and reprint a letter, and it is the only record of exactly
         * what was filled in.</p>
         */
        private boolean keepXlsx = true;
    }

    /** The spreadsheet that is filled in. */
    @Data
    public static class Template {

        /**
         * Classpath location of the template.
         *
         * <p>Read as a stream, never a file: the template lives inside the
         * packaged jar, where it has no filesystem path.</p>
         */
        private String path = "deal_confirmation/ATSPL Deal Format.xlsx";

        /**
         * Sheet to fill. <strong>Every sheet name in this workbook ends with a
         * space</strong>, and the lookup trims both sides, so either spelling
         * works. YAML strips trailing whitespace from an unquoted scalar, which
         * is why the default here carries none.
         */
        private String sheetName = "PSU Private Sale";
    }

    /** How the PDF is produced. */
    @Data
    public static class Pdf {

        /**
         * Whether to render a PDF.
         *
         * <p>{@code false} produces the spreadsheet only. Useful while the
         * LibreOffice dependency is being sorted out in an environment, and as
         * a deliberate degraded mode: the document is still produced and
         * recorded, just not rendered.</p>
         */
        private boolean enabled = true;

        /** Absolute path to {@code soffice}, or a bare name resolved from PATH. */
        private String sofficePath = "soffice";

        /**
         * Ceiling on a single conversion.
         *
         * <p>A hung engine must not hold an HTTP request open indefinitely. On
         * expiry the process is killed and generation fails.</p>
         */
        private Duration timeout = Duration.ofSeconds(30);

        /**
         * How many conversions may run at once.
         *
         * <p>Each conversion is a separate {@code soffice} process taking a few
         * hundred megabytes, and there is no queue: without a bound, N
         * simultaneous purchases start N processes. Requests over the limit
         * wait, bounded by {@link #timeout}.</p>
         */
        private int maxConcurrentConversions = 2;
    }

    /** Values the letter prints that no single deal supplies. */
    @Data
    public static class Deal {

        /**
         * Face value used to turn a bond quantity into a quantum.
         *
         * <p>{@code Bond} has no face-value field; 100 is the market convention
         * and is the only value consistent with the persisted
         * {@code total_amount}, which is {@code price x quantity}. Any other
         * value makes the printed quantum disagree with the amount charged.</p>
         */
        private int faceValue = 100;

        /** Days from the deal date to the value (settlement) date. T+0 today. */
        private int valueDateOffsetDays = 0;

        /**
         * Stamp duty printed on the letter.
         *
         * <p>Zero pending confirmation of the rule. The template carries a bare
         * literal with no formula, so neither the rate nor its base can be
         * derived from it.</p>
         */
        private java.math.BigDecimal stampDuty = java.math.BigDecimal.ZERO;

        /**
         * Which side of the trade the letter describes.
         *
         * <p>The customer is buying from us, so it is our sale. A purchase-side
         * letter would be a different document with a different sheet.</p>
         */
        private String transactionType = "Our Sale";
    }

    /** Who we are, as printed on the letter. */
    @Data
    public static class Organisation {

        private String beneficiaryName = "All Time Securities pvt. Ltd.";

        private String pan = "AAHCA7743E";

        private String cmBpId = "IN619994";

        private String cmName = "Indian Clearing Corporation Ltd";

        private String ifscCode = "ICLL0000001";

        private String marketType = "ICDM(T+0)";

        private String banker = "ICCL Or RBI";

        private String modeOfDelivery = "ICCL";

        /** Left blank: no source for it, and a guess would be a wrong number. */
        private String accountNumber = "";

        /** Left blank: settlement numbers are issued per deal by the clearing house. */
        private String settlementNumber = "";
    }
}
