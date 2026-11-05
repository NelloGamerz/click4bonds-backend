package com.click4bonds.app.Modules.Offer.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * Picks the design an offer is drawn in.
 *
 * <p>
 * Every {@link OfferDesignStrategy} bean is registered by its own name, so a new
 * design needs no change here. A request that names no design gets
 * {@link #DEFAULT_DESIGN}.
 */
@Component
public class OfferDesignStrategyFactory {

    /** The design used when a request does not name one. */
    public static final String DEFAULT_DESIGN = "DIWALI";

    private final Map<String, OfferDesignStrategy> designs;

    private final OfferDesignStrategy fallback;

    public OfferDesignStrategyFactory(List<OfferDesignStrategy> strategies) {

        Map<String, OfferDesignStrategy> byName = new LinkedHashMap<>();

        for (OfferDesignStrategy strategy : strategies) {
            byName.put(strategy.design().toUpperCase(Locale.ENGLISH), strategy);
        }

        this.designs = Map.copyOf(byName);

        this.fallback = byName.get(DEFAULT_DESIGN);

        if (this.fallback == null) {
            throw new IllegalStateException(
                    "No offer design named " + DEFAULT_DESIGN + " is registered");
        }
    }

    /**
     * @param design the requested design, or null/blank for the default
     * @return the strategy to draw with, never null
     * @throws BadRequestException when the named design is not registered
     */
    public OfferDesignStrategy strategyFor(String design) {

        if (design == null || design.isBlank()) {
            return fallback;
        }

        OfferDesignStrategy strategy = designs.get(design.trim().toUpperCase(Locale.ENGLISH));

        if (strategy == null) {
            throw new BadRequestException(
                    "Unknown offer design '" + design + "'. Available designs: "
                            + String.join(", ", designs.keySet()));
        }

        return strategy;
    }
}
