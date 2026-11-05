package com.click4bonds.app.Modules.Offer.Service;

import com.click4bonds.app.Modules.Offer.Dto.OfferDetails;

/**
 * One visual design an offer can be drawn in.
 *
 * <p>
 * A design is a whole document — palette, layout and wording — so it is a
 * strategy rather than a branch in one renderer. Adding a design means adding a
 * bean that implements this interface; nothing else changes, because
 * {@link OfferDesignStrategyFactory} discovers the beans and routes by
 * {@link #design()}.
 */
public interface OfferDesignStrategy {

    /**
     * The name a request uses to select this design, upper-cased, e.g.
     * {@code DIWALI}.
     */
    String design();

    /**
     * Draws the offer.
     *
     * @param offer the resolved offer values
     * @return the PDF bytes, never empty
     * @throws com.click4bonds.app.Modules.Document.Exception.DocumentGenerationException
     *         when the document cannot be rendered
     */
    byte[] render(OfferDetails offer);
}
