package com.click4bonds.app.Modules.Order.Service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Stand-in for a payment gateway.
 *
 * <p>Exists so the order flow can be exercised end to end before a real
 * processor is wired in. It takes no money and contacts nothing: it records what
 * it was asked to settle and returns a reference, which is exactly the shape a
 * real integration has from the caller's point of view — an input, a
 * side-effecting round trip, and an identifier to quote.</p>
 *
 * <p><strong>It always succeeds.</strong> There is no failure branch to
 * exercise, so a caller cannot use this to test what a declined payment should
 * do. When a real processor replaces this class it will need to be able to fail,
 * and the checkout in {@link BondOrderCheckoutService} is the single place that
 * has to learn about it: it runs inside the transaction that reserves inventory
 * and writes the deal, so a failure there rolls all of it back and leaves the
 * order untouched.</p>
 *
 * <p>The reference is prefixed {@code PAY-DUMMY-} so a value in a response, a
 * log or a database can never be mistaken for a real settlement
 * identifier.</p>
 */
@Component
@Slf4j
public class DummyPaymentProcessor {

    public static final String REFERENCE_PREFIX = "PAY-DUMMY-";

    /**
     * Simulates settling an order.
     *
     * @param orderNumber the order being paid, for the log only
     * @return a reference the caller can quote, never null
     */
    public String process(String orderNumber) {

        String reference = REFERENCE_PREFIX
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();

        log.info(
                "Dummy payment processed: orderNumber={} paymentReference={} (no money moved)",
                orderNumber,
                reference);

        return reference;
    }
}
