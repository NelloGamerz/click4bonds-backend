package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Model.DealConfirmation;
import com.click4bonds.app.Modules.DealConfirmation.Repository.DealConfirmationRepository;
import com.click4bonds.app.Modules.Document.Exception.DocumentStorageException;
import com.click4bonds.app.Modules.Document.Service.DocumentStorage;

/**
 * Reading a letter back for download.
 *
 * <p>Two things are worth pinning. The first is that the row's address is turned
 * back into a key before the store is asked for anything — the address is what is
 * persisted, the key is what the bucket understands, and passing one where the
 * other belongs would look like a missing document. The second is the scoping:
 * another customer's deal must be indistinguishable from one that does not
 * exist, so the lookup is by reference <em>and</em> customer rather than by
 * reference with a check afterwards.</p>
 */
@ExtendWith(MockitoExtension.class)
class DealConfirmationDocumentReaderTest {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String REFERENCE = "DC-20260929-000001";

    private static final String KEY = "2026/09/" + REFERENCE + ".pdf";

    private static final String LOCATION =
            "https://abc123.r2.cloudflarestorage.com/click4bonds-documents/" + KEY;

    private static final byte[] CONTENT = "letter".getBytes(StandardCharsets.UTF_8);

    @Mock
    private DealConfirmationRepository dealConfirmationRepository;

    @Mock
    private DocumentStorage documentStorage;

    @InjectMocks
    private DealConfirmationDocumentReader reader;

    @Test
    void resolvesTheRecordedAddressToAKeyBeforeReading() {

        when(dealConfirmationRepository.findByDealReferenceAndCustomer_Id(REFERENCE, CUSTOMER_ID))
                .thenReturn(Optional.of(dealWith(LOCATION)));
        when(documentStorage.keyOf(LOCATION)).thenReturn(KEY);
        when(documentStorage.read(KEY)).thenReturn(CONTENT);

        DealConfirmationDocumentReader.DownloadedDocument document =
                reader.read(CUSTOMER_ID, REFERENCE);

        verify(documentStorage).read(KEY);

        assertThat(document.content()).isEqualTo(CONTENT);
        assertThat(document.fileName()).isEqualTo(REFERENCE + ".pdf");
        assertThat(document.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void servesTheSpreadsheetWhenThatIsWhatWasRecorded() {

        /*
         * The format is read from the address, so it stays the same value that
         * produced the key. A letter generated with PDF rendering switched off is
         * a spreadsheet, and it must download as one.
         */
        String spreadsheetLocation =
                "https://abc123.r2.cloudflarestorage.com/click4bonds-documents/"
                        + "2026/09/" + REFERENCE + ".xlsx";

        when(dealConfirmationRepository.findByDealReferenceAndCustomer_Id(REFERENCE, CUSTOMER_ID))
                .thenReturn(Optional.of(dealWith(spreadsheetLocation)));
        when(documentStorage.keyOf(spreadsheetLocation))
                .thenReturn("2026/09/" + REFERENCE + ".xlsx");
        when(documentStorage.read("2026/09/" + REFERENCE + ".xlsx")).thenReturn(CONTENT);

        DealConfirmationDocumentReader.DownloadedDocument document =
                reader.read(CUSTOMER_ID, REFERENCE);

        assertThat(document.fileName()).isEqualTo(REFERENCE + ".xlsx");
        assertThat(document.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    void reportsAnotherCustomersDealAsMissing() {

        when(dealConfirmationRepository.findByDealReferenceAndCustomer_Id(REFERENCE, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.read(CUSTOMER_ID, REFERENCE))
                .isInstanceOf(ResourceNotFoundException.class);

        /*
         * And storage is never consulted, so a deal belonging to someone else
         * cannot be probed for the existence of a document.
         */
        verifyNoInteractions(documentStorage);
    }

    @Test
    void reportsADealWithNoDocumentAsMissing() {

        when(dealConfirmationRepository.findByDealReferenceAndCustomer_Id(REFERENCE, CUSTOMER_ID))
                .thenReturn(Optional.of(dealWith(null)));

        /*
         * A real deal whose generation failed or was switched off. Reported like
         * a missing deal rather than as an error, because it is a legitimate
         * state and the deal itself is unaffected.
         */
        assertThatThrownBy(() -> reader.read(CUSTOMER_ID, REFERENCE))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(documentStorage);
    }

    @Test
    void surfacesAnAddressItCannotResolve() {

        /*
         * An address naming a different endpoint or bucket than the one
         * configured — a data/configuration mismatch. It must not be swallowed
         * into "no document": that reads as "generate one", and regenerating
         * overwrites.
         */
        when(dealConfirmationRepository.findByDealReferenceAndCustomer_Id(REFERENCE, CUSTOMER_ID))
                .thenReturn(Optional.of(dealWith(LOCATION)));
        when(documentStorage.keyOf(LOCATION))
                .thenThrow(new DocumentStorageException("was not written by this endpoint"));

        assertThatThrownBy(() -> reader.read(CUSTOMER_ID, REFERENCE))
                .isInstanceOf(DocumentStorageException.class);
    }

    private DealConfirmation dealWith(String documentR2Path) {

        DealConfirmation deal = new DealConfirmation();

        deal.setDocumentR2Path(documentR2Path);

        return deal;
    }
}
