package com.click4bonds.app.Modules.DealConfirmation.Config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.click4bonds.app.Modules.DealConfirmation.Service.AtSplDealConfirmationDocumentService;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationCellMap;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationDocumentService;
import com.click4bonds.app.Modules.DealConfirmation.Service.DealConfirmationSheetValuesFactory;
import com.click4bonds.app.Modules.DealConfirmation.Service.NoOpDealConfirmationDocumentService;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Document.Service.DocumentStorage;
import com.click4bonds.app.Modules.Document.Service.PdfConverter;
import com.click4bonds.app.Modules.Document.Service.XlsxTemplateWriter;

/**
 * Chooses which document implementation a deal confirmation uses.
 *
 * <p>Both candidates are declared here as {@code @Bean} methods rather than
 * being component-scanned, because the fallback is selected with
 * {@code @ConditionalOnMissingBean} and that condition is evaluated in an
 * undefined order against a scanned candidate. Declaring them together makes the
 * ordering explicit and the selection deterministic.</p>
 *
 * <p>{@code document.enabled=false} wires the no-op implementation, so an
 * environment without LibreOffice can still create deals. That state is
 * reachable and testable; making the real bean {@code @Primary} instead would
 * leave both beans constructed, the no-op logging a misleading line, and the
 * "off" path impossible to exercise.</p>
 */
@Configuration
public class DealConfirmationDocumentConfig {

    /**
     * The real implementation: fills the template, renders a PDF and stores both.
     *
     * <p>On by default. Its dependencies are injected rather than exercised, so
     * constructing it touches no filesystem and starts no process — a
     * context-load test does not need LibreOffice installed, and the first
     * failure is reported when a document is actually generated.</p>
     */
    @Bean
    @ConditionalOnProperty(
            name = "document.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public DealConfirmationDocumentService atSplDealConfirmationDocumentService(
            XlsxTemplateWriter templateWriter,
            PdfConverter pdfConverter,
            DocumentStorage documentStorage,
            DealConfirmationSheetValuesFactory sheetValuesFactory,
            DealConfirmationCellMap cellMap,
            DocumentProperties documentProperties) {

        return new AtSplDealConfirmationDocumentService(
                templateWriter,
                pdfConverter,
                documentStorage,
                sheetValuesFactory,
                cellMap,
                documentProperties);
    }

    /**
     * The fallback, used only when the real implementation is switched off.
     *
     * <p>It exists so that disabling documents removes the letters, not the
     * application: without it, {@code document.enabled=false} would leave no
     * {@code DealConfirmationDocumentService} bean at all and the context would
     * fail to start.</p>
     */
    @Bean
    @ConditionalOnMissingBean(DealConfirmationDocumentService.class)
    public DealConfirmationDocumentService noOpDealConfirmationDocumentService() {
        return new NoOpDealConfirmationDocumentService();
    }
}
