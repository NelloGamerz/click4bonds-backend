package com.click4bonds.app.Modules.Order.Service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.click4bonds.app.Modules.Common.Exceptions.ResourceNotFoundException;
import com.click4bonds.app.Modules.DealConfirmation.Dto.DealConfirmationDocumentEvent;
import com.click4bonds.app.Modules.DealConfirmation.Producer.DealConfirmationDocumentProducer;
import com.click4bonds.app.Modules.Document.Config.DocumentProperties;
import com.click4bonds.app.Modules.Order.Dto.BondOrderPaymentResponse;
import com.click4bonds.app.Modules.Order.Model.BondOrder;
import com.click4bonds.app.Modules.Order.Repository.BondOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Entry point for paying an order.
 *
 * <p>Wraps the transactional {@link BondOrderCheckoutService} with the two things
 * that must happen outside its transaction: recognising two payments for the same
 * order that collide, and asking for the deal's confirmation letter to be
 * produced.</p>
 *
 * <p><strong>Deliberately not transactional.</strong> Catching
 * {@link DataIntegrityViolationException} inside the checkout would be useless —
 * by then the transaction is marked rollback-only, and continuing to use it
 * would fail at commit with a different exception. The retry has to run in a
 * fresh transaction, so the catch lives here, exactly as
 * {@code DealConfirmationService} catches the same collision for the customer
 * purchase flow.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BondOrderPaymentService {

    private final BondOrderCheckoutService checkoutService;
    private final DealConfirmationDocumentProducer documentProducer;
    private final DocumentProperties documentProperties;
    private final BondOrderRepository orderRepository;

    /**
     * Pays for an order on behalf of the account that placed it.
     *
     * <p>For the admin testing path, where the caller is a member of staff
     * rather than the buyer. The account paying is read from the order rather
     * than taken from the caller, so {@link #payOrder}'s ownership check is left
     * meaning what it always did — the order does belong to that customer — and
     * the authority to invoke this comes from the module gate on the endpoint,
     * not from owning the order.</p>
     *
     * <p>Passing the admin's own id here instead would be refused by that check,
     * which is the behaviour that made this method necessary: an order placed
     * for a customer cannot be paid by a staff account pretending to be them.</p>
     *
     * @param orderId the order to pay
     * @return the order's new state, the deal it produced and the holding it
     *         landed in
     * @throws ResourceNotFoundException when no such order exists
     */
    public BondOrderPaymentResponse payOrderAsAdmin(UUID orderId) {

        BondOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderId));

        return payOrder(order.getCustomer().getId(), orderId);
    }

    /**
     * Pays for an order, or replays a payment already made against it.
     *
     * @param callerId the account paying, from the JWT; must own the order
     * @param orderId  the order to pay
     * @return the order's new state, the deal it produced and the holding it
     *         landed in; {@code replayed} is true when nothing further was
     *         bought
     */
    public BondOrderPaymentResponse payOrder(UUID callerId, UUID orderId) {

        BondOrderCheckoutService.CheckoutResult result = attempt(callerId, orderId);

        /*
         * After the checkout has returned, which means after its transaction has
         * committed. Publishing before that would ask for a letter for a deal
         * that could still roll back.
         */
        requestDocument(result);

        return result.response();
    }

    /**
     * Runs the checkout, collapsing a concurrent duplicate onto the deal that
     * won.
     */
    private BondOrderCheckoutService.CheckoutResult attempt(UUID callerId, UUID orderId) {

        try {

            return checkoutService.checkout(callerId, orderId);

        } catch (DataIntegrityViolationException duplicate) {

            /*
             * Two payments for one order arrived at the same instant and the
             * unique index on the deal's (customer, idempotency_key) let only one
             * of them through. The losing transaction has already rolled back,
             * so no units were taken twice and no second holding was added.
             * Re-running the checkout now finds the order CONFIRMED — the winner
             * committed, or the index would not have refused this one — and
             * replays it, which is the answer the caller's retry should get.
             */
            log.info(
                    "Concurrent duplicate payment for order {}; replaying the winning deal",
                    orderId);

            return checkoutService.checkout(callerId, orderId);
        }
    }

    /**
     * Asks for the deal's confirmation letter — the Excel and the PDF stored in
     * R2 — to be produced.
     *
     * <p>Follows {@code DealConfirmationService.requestDocument}: the letter is
     * not built on the request thread. The request is published to Kafka, where
     * {@code DealConfirmationDocumentConsumer} fills the spreadsheet, renders the
     * PDF, stores both and records the address against the deal, moving it to
     * {@code CONFIRMATION_GENERATED}.</p>
     *
     * <p>Nothing happens on a replayed payment: the letter was asked for by the
     * payment that did the work, and a second request would render and store a
     * duplicate of a letter that already exists.</p>
     *
     * <p>A failure here is logged, never propagated. The purchase is already
     * confirmed, its inventory reserved and its units in the holding; failing the
     * request now would tell the caller their payment did not happen when it did.
     * The deal stays {@code CREATED} with no document, which is what a failed
     * generation leaves behind in the customer flow too.</p>
     */
    private void requestDocument(BondOrderCheckoutService.CheckoutResult result) {

        if (!result.hasDocumentToProduce()) {
            return;
        }

        /*
         * With documents switched off there is no letter to ask for, and
         * publishing anyway would fill the topic with requests whose only
         * possible outcome is the no-op implementation declining them.
         */
        if (!documentProperties.isEnabled()) {

            log.debug(
                    "Documents are switched off; no letter requested for deal {}",
                    result.documentData().dealReference());

            return;
        }

        try {

            documentProducer.publish(new DealConfirmationDocumentEvent(
                    result.dealId(),
                    result.documentData(),
                    true));

        } catch (RuntimeException failure) {

            log.error(
                    "Deal {} was created but its document request could not be published;"
                            + " the deal itself stands",
                    result.documentData().dealReference(),
                    failure);
        }
    }
}
