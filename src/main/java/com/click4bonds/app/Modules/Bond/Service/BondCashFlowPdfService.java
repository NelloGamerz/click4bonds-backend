package com.click4bonds.app.Modules.Bond.Service;

import com.click4bonds.app.Modules.Bond.Dto.BondCashFlowResponse;
import com.click4bonds.app.Modules.Bond.Models.Bond;

/**
 * Renders a projected cash flow as a downloadable PDF statement.
 *
 * <p>
 * The statement is the same projection the JSON endpoint returns, laid out for
 * a reader rather than a client: the bond's details, the amounts invested and
 * received, and the dated schedule.
 *
 * <p>
 * Kept behind an interface because the layout is the only part of the endpoint
 * that reaches for a rendering library, and a caller that only wants the JSON
 * should not have to carry it.
 */
public interface BondCashFlowPdfService {

    /**
     * Renders one bond's projected cash flow as a PDF.
     *
     * @param bond     the bond the schedule was projected for; supplies the
     *                 details block (rating, maturity, coupon, security type)
     * @param cashFlow the projection, already scaled to the requested quantity
     * @return the PDF bytes, never empty
     * @throws com.click4bonds.app.Modules.Common.Exceptions.InternalServerException
     *         when the document cannot be rendered
     */
    byte[] render(Bond bond, BondCashFlowResponse cashFlow);

    /**
     * The name to offer the download under, without any path.
     *
     * <p>
     * Derived from the ISIN so two downloads do not overwrite each other.
     *
     * @param bond the bond the statement is for
     * @return a {@code .pdf} filename
     */
    String fileName(Bond bond);
}
