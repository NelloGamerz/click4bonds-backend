package com.click4bonds.app.Modules.Bond.Dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One page of issuers, cursor paginated.
 *
 * The caller passes {@code nextCursor} back as the {@code cursor}
 * query parameter to fetch the following page. It is NULL on the
 * last page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssuerPageResponse {

    private List<IssuerResponse> items;

    /**
     * Number of items in this page.
     */
    private int size;

    private boolean hasNext;

    private String nextCursor;
}
