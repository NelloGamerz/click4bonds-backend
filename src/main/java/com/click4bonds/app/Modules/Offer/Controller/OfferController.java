package com.click4bonds.app.Modules.Offer.Controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Offer.Dto.CreateOfferRequest;
import com.click4bonds.app.Modules.Offer.Service.OfferService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Offer API.
 *
 * <p>
 * A back-office account sends a bond ISIN with at most one of a YTM or a price;
 * the server derives the other figure (or, when neither is sent, reads both from
 * the bond) and answers with the rendered offer PDF. No offer is stored and the
 * PDF is never uploaded — it is produced for the request and returned.
 */
@RestController
@RequestMapping("/api/admin/offers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public class OfferController {

    private final OfferService offerService;

    /**
     * Draws an offer for one bond.
     *
     * @param request ISIN plus at most one of YTM or price, and an optional design
     * @return the PDF as an attachment named after the bond
     */
    /*
     * No `produces` here: declaring application/pdf on the mapping makes the
     * route's producible types PDF-only, so when the request is rejected the
     * global handler's JSON ApiError has no acceptable representation and the
     * real 4xx turns into a 406. The response's own content type is set below.
     */
    @PostMapping
    public ResponseEntity<byte[]> createOffer(
            @Valid @RequestBody CreateOfferRequest request) {

        byte[] pdf = offerService.renderOffer(request);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("Offer-" + request.isin().trim().toUpperCase() + ".pdf")
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }
}
