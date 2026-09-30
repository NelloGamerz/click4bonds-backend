package com.click4bonds.app.Modules.DealConfirmation.Service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Picks the layout a deal confirmation letter is printed on.
 *
 * <p>A Sovereign-rated bond is a government security and is confirmed on the
 * G-Sec sell sheet; everything else is confirmed on the corporate PSU sheet,
 * exactly as before this class existed. That is the whole rule, and it lives here
 * — in one readable place — rather than as a branch inside the fill or a lookup
 * inside either strategy. A strategy knows how to lay a letter out; which letter
 * a given deal deserves is a decision about deals, not about layouts.</p>
 *
 * <p><strong>The rating reaches here on the snapshot.</strong>
 * {@code DealConfirmationDocumentData.rating} is captured inside the deal's
 * transaction, because the document step runs after it commits and may not load a
 * {@code Bond}. So this decides from a value frozen at purchase time: a later edit
 * to the bond's rating does not re-letter an old deal, which is how every other
 * field on that record already behaves.</p>
 *
 * <p><strong>The G-Sec side is a TODO that fails loudly.</strong>
 * {@link GsecSellSheetStrategy#toCells} throws, so a Sovereign deal currently
 * produces no letter and is left retryable. See that class for why an empty map
 * would be the worse failure.</p>
 */
@Component
@RequiredArgsConstructor
public class DealConfirmationSheetStrategyFactory {

    /**
     * The rating that means "government security".
     *
     * <p>Matched case-insensitively against the whole trimmed value, because
     * {@code Bond.rating} is free text rather than an enum: what the data actually
     * holds cannot be constrained by the type, so the match is written to be
     * explicit about what it does and does not accept. {@code "Sovereign"} and
     * {@code "SOVEREIGN"} match; {@code "Sovereign GOLD"} and {@code "AAA"} do
     * not, and fall through to the corporate letter.</p>
     */
    static final String SOVEREIGN_RATING = "Sovereign";

    private final PsuPrivateSaleSheetStrategy psuPrivateSaleSheet;

    private final GsecSellSheetStrategy gsecSellSheet;

    /**
     * @param rating the bond's rating as it stood when the deal was struck, or
     *               null when the bond has none
     * @return the layout the deal is printed on — never null
     */
    public DealConfirmationSheetStrategy strategyFor(String rating) {

        return isSovereign(rating) ? gsecSellSheet : psuPrivateSaleSheet;
    }

    /**
     * Whether a rating value means a government security.
     *
     * <p>Null is not a Sovereign: a bond with no rating recorded is lettered on
     * the corporate sheet, which is what every deal did before this rule existed.
     * Treating a missing rating as Sovereign would silently move unrated bonds
     * onto a letter with TDS on it.</p>
     */
    static boolean isSovereign(String rating) {

        return rating != null && rating.trim().equalsIgnoreCase(SOVEREIGN_RATING);
    }
}
