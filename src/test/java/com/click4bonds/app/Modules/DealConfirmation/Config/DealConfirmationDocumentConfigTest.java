package com.click4bonds.app.Modules.DealConfirmation.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
 * Which document implementation a deal confirmation gets.
 *
 * <p>Worth pinning because both candidates implement the same interface and the
 * selection is a condition rather than a type. Getting it wrong is silent: the
 * application starts, deals are created, and the letters simply never appear —
 * or a machine with no LibreOffice fails every purchase instead of skipping the
 * render.</p>
 *
 * <p>The engine beans are stubbed. Nothing here should touch the filesystem or
 * start a process; a configuration test that needs LibreOffice installed would
 * not run where it is most needed.</p>
 */
class DealConfirmationDocumentConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DealConfirmationDocumentConfig.class)
            .withBean(DocumentProperties.class)
            .withBean(XlsxTemplateWriter.class, () -> mock(XlsxTemplateWriter.class))
            .withBean(PdfConverter.class, () -> mock(PdfConverter.class))
            .withBean(DocumentStorage.class, () -> mock(DocumentStorage.class))
            .withBean(DealConfirmationSheetValuesFactory.class,
                    () -> new DealConfirmationSheetValuesFactory(new DocumentProperties()))
            .withBean(DealConfirmationCellMap.class,
                    () -> new DealConfirmationCellMap(new DocumentProperties()));

    @Test
    void wiresTheRealImplementationByDefault() {

        runner.run(context -> {

            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DealConfirmationDocumentService.class);
            assertThat(context.getBean(DealConfirmationDocumentService.class))
                    .isInstanceOf(AtSplDealConfirmationDocumentService.class);
        });
    }

    @Test
    void wiresTheNoOpWhenDocumentsAreSwitchedOff() {

        runner.withPropertyValues("document.enabled=false").run(context -> {

            /*
             * The point of the fallback. Without it, disabling documents would
             * leave no DealConfirmationDocumentService bean at all and the
             * context would fail to start — turning "no letters here" into "the
             * application will not run here".
             */
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DealConfirmationDocumentService.class);
            assertThat(context.getBean(DealConfirmationDocumentService.class))
                    .isInstanceOf(NoOpDealConfirmationDocumentService.class);
        });
    }

    @Test
    void neverWiresBoth() {

        runner.withPropertyValues("document.enabled=true").run(context -> {

            assertThat(context).hasSingleBean(DealConfirmationDocumentService.class);
            assertThat(context.getBean(DealConfirmationDocumentService.class))
                    .isInstanceOf(AtSplDealConfirmationDocumentService.class);
        });
    }
}
