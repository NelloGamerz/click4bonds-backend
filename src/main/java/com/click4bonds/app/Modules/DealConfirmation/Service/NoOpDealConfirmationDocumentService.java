package com.click4bonds.app.Modules.DealConfirmation.Service;

import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocument;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentData;

import lombok.extern.slf4j.Slf4j;

/**
 * Placeholder for {@link DealConfirmationDocumentService}.
 *
 * <p>Produces no document. It exists so the deal flow can be wired end to end
 * today — the call site, the logging and the status handling are all real — and
 * so that implementing the document step is a one-line swap: replace this bean
 * with the real implementation, whose {@link #generate} will fill
 * {@code Deal Format.xlsx}, convert it to PDF and store it.</p>
 *
 * <p>The template file is already in the repository at
 * {@code src/main/resources/deal_confirmation/Deal Format.xlsx}; it is not read
 * yet on purpose (Apache POI is not a dependency of this project).</p>
 */
@Service
@Slf4j
public class NoOpDealConfirmationDocumentService implements DealConfirmationDocumentService {

    @Override
    public DealConfirmationDocument generate(DealConfirmationDocumentData dealConfirmation) {

        log.info(
                "Deal confirmation document generation is not implemented yet; "
                        + "skipping for deal {}", dealConfirmation.dealReference());

        return DealConfirmationDocument.none();
    }
}
