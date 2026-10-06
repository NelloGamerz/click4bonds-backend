package com.click4bonds.app.Modules.DealConfirmation.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.click4bonds.app.Modules.Document.Config.DocumentProperties;

/**
 * Which layout a deal is printed on.
 *
 * <p>Worth pinning because the rule is a string comparison against free text, and
 * both ways it can go wrong are silent. A Sovereign deal routed to the corporate
 * sheet prints a G-Sec on a letter that charges it stamp duty instead of TDS; an
 * ordinary deal routed to the G-Sec sheet fails to generate a letter at all.
 * Neither raises an error of its own.</p>
 *
 * <p>{@code Bond.rating} is a plain {@code String(100)} with no enum and no
 * constraint, so the exact values below are the contract: what the data holds is
 * not enforced by a type, which is precisely why it is asserted here.</p>
 */
class DealConfirmationSheetStrategyFactoryTest {

    private final DocumentProperties properties = new DocumentProperties();

    private final PsuPrivateSaleSheetStrategy psuPrivateSale =
            new PsuPrivateSaleSheetStrategy(properties);

    private final GsecSellSheetStrategy gsecSellSheet =
            new GsecSellSheetStrategy(properties);

    private final DealConfirmationSheetStrategyFactory factory =
            new DealConfirmationSheetStrategyFactory(psuPrivateSale, gsecSellSheet);

    // =========================================================
    // SOVEREIGN -> THE G-SEC SHEET
    // =========================================================

    @Test
    void routesASovereignRatedBondToTheGsecSellSheet() {

        assertThat(factory.strategyFor("Sovereign")).isSameAs(gsecSellSheet);
    }

    @Test
    void matchesTheRatingRegardlessOfCaseOrSurroundingSpace() {

        /*
         * The value is free text typed into an admin form, so it arrives in
         * whatever case and with whatever whitespace the operator used. None of
         * that changes which sheet the deal belongs on.
         */
        for (String rating : new String[] {
                "SOVEREIGN", "sovereign", "SoVeReIgN", " Sovereign ", "\tSovereign\n" }) {

            assertThat(factory.strategyFor(rating))
                    .as("rating [%s] should route to the G-Sec sheet", rating)
                    .isSameAs(gsecSellSheet);
        }
    }

    // =========================================================
    // EVERYTHING ELSE -> THE CORPORATE SHEET
    // =========================================================

    @Test
    void routesEverythingElseToTheCorporateSheet() {

        for (String rating : new String[] {
                "AAA", "AA+", "CRISIL AAA", "Sovereign GOLD", "GOI", "IN0020230044" }) {

            assertThat(factory.strategyFor(rating))
                    .as("rating [%s] should keep the corporate letter it has today", rating)
                    .isSameAs(psuPrivateSale);
        }
    }

    @Test
    void routesAnUnratedBondToTheCorporateSheet() {

        /*
         * Null is not a Sovereign. Treating a missing rating as one would move
         * every unrated bond onto a letter with TDS on it, silently, and this is
         * the value a bond with no rating recorded actually produces
         * (DealConfirmationDocumentData.from).
         */
        assertThat(factory.strategyFor(null)).isSameAs(psuPrivateSale);
    }

    @Test
    void routesABlankRatingToTheCorporateSheet() {

        /*
         * A blank string is a rating that was never filled in, not a Sovereign.
         * Bond.rating has no not-blank constraint, so this is reachable.
         */
        for (String rating : new String[] { "", "   " }) {

            assertThat(factory.strategyFor(rating)).isSameAs(psuPrivateSale);
        }
    }

    // =========================================================
    // THE PREDICATE ITSELF
    // =========================================================

    @Test
    void isSovereignAcceptsOnlyTheWholeTrimmedWord() {

        assertTrue(DealConfirmationSheetStrategyFactory.isSovereign("Sovereign"));
        assertTrue(DealConfirmationSheetStrategyFactory.isSovereign(" sovereign "));

        /*
         * A substring match would accept these, and would also accept unrelated
         * labels that happen to contain the word — "Sovereign GOLD" is a
         * different instrument. The whole-value comparison is deliberate.
         */
        assertFalse(DealConfirmationSheetStrategyFactory.isSovereign("Sovereign GOLD"));
        assertFalse(DealConfirmationSheetStrategyFactory.isSovereign("SOVEREIGN BOND"));
        assertFalse(DealConfirmationSheetStrategyFactory.isSovereign(null));
    }

    // =========================================================
    // THE ROUTING IS USABLE
    // =========================================================

    @Test
    void everyRouteReturnsAStrategyThatNamesASheet() {

        /*
         * The factory never returns null, and whatever it returns can name the
         * sheet it fills. A null here would surface far away as a
         * NullPointerException inside the fill.
         */
        for (String rating : new String[] { null, "", "AAA", "Sovereign" }) {

            DealConfirmationSheetStrategy strategy = factory.strategyFor(rating);

            assertThat(strategy).isNotNull();
            assertThat(strategy.sheetName()).isNotBlank();
        }
    }
}
