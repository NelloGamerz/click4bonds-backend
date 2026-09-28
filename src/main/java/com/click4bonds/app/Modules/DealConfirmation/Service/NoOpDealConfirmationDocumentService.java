package com.click4bonds.app.Modules.DealConfirmation.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;

import lombok.extern.slf4j.Slf4j;

/**
 * The "documents are switched off" implementation of
 * {@link DealConfirmationDocumentService}.
 *
 * <p>Produces no document, and says so at information level. It is wired when
 * {@code document.enabled=false}, which is the setting for an environment
 * without LibreOffice — a developer laptop or CI — where generation would
 * otherwise fail on every purchase and fill the logs with errors that are not
 * anyone's problem.</p>
 *
 * <p>It is not a placeholder any more. The real implementation is
 * {@link AtSplDealConfirmationDocumentService}; this one is the deliberate
 * degraded mode, and the two are selected in
 * {@link com.click4bonds.app.Modules.DealConfirmation.Config.DealConfirmationDocumentConfig}.</p>
 *
 * <p><strong>Deliberately not annotated {@code @Service}.</strong> Bean
 * selection is by {@code @ConditionalOnMissingBean}, which is evaluated in an
 * undefined order when the candidate comes from component scanning — a known
 * source of a fallback that is sometimes the one wired. Declaring both as
 * {@code @Bean} methods in one configuration class makes the order explicit.</p>
 */
@Slf4j
public class NoOpDealConfirmationDocumentService implements DealConfirmationDocumentService {

    @Override
    public DealConfirmationDocument generate(DealConfirmationDocumentData dealConfirmation) {

        log.info(
                "Document generation is switched off (document.enabled=false); "
                        + "no confirmation letter produced for deal {}",
                dealConfirmation.dealReference());

        return DealConfirmationDocument.none();
    }
}
