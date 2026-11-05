package com.click4bonds.app.Modules.DealConfirmation.Service;

import org.springframework.stereotype.Component;

/**
 * The corporate bond letter: the layout every deal gets unless the bond is a
 * Sovereign.
 *
 * <p>It is the default {@link DealConfirmationLayout#CORPORATE}, so it overrides
 * nothing. It exists as its own type because which letter a deal is printed on
 * is chosen by {@link DealConfirmationSheetStrategyFactory}, and both candidates
 * are named there in one place.</p>
 */
@Component
public class PsuPrivateSaleSheetStrategy implements DealConfirmationSheetStrategy {
}
