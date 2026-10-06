package com.click4bonds.app.Modules.Analytics.Controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsEventSearchResponse;
import com.click4bonds.app.Modules.Analytics.Dto.AnalyticsUserEventsResponse;
import com.click4bonds.app.Modules.Analytics.Model.AnalyticsEventType;
import com.click4bonds.app.Modules.Analytics.Service.AdminAnalyticsQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Administrator access to analytics history, one user at a time or across all
 * of them.
 *
 * <p>Every other read of analytics data in this application is scoped to the
 * caller's own id. These endpoints are the one place where an administrator sees
 * somebody else's activity — named explicitly on the per-user endpoint, and
 * unconstrained on the search — which is why they are role-gated on the whole
 * controller and why each call is logged against the administrator who made it.
 * Reading another person's activity is exactly the kind of access that has to be
 * answerable afterwards.</p>
 *
 * <p>The search endpoint is the broader of the two: it can return rows belonging
 * to any user, and unlike the per-user read it is not narrowed by a path
 * variable, so the {@code eventType} filter is what keeps it from being a scan
 * of the whole table — {@code event_type} leads the table's sorting key.</p>
 *
 * <p>The role gate is {@code @PreAuthorize}, which is only enforced because
 * {@code SecurityConfig} carries {@code @EnableMethodSecurity}. Removing that
 * annotation turns this controller's check off along with every other one;
 * nothing in the filter chain covers {@code /api/admin/**} by itself.</p>
 */
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminAnalyticsController {

    private final AdminAnalyticsQueryService adminAnalyticsQueryService;

    /**
     * Returns one page of a user's events, newest first, with totals.
     *
     * <p>Paging is cursor based. The first call omits {@code cursor}; each
     * answer carries {@code nextCursor} while {@code hasNext} is true, and the
     * caller passes that value back verbatim. It is opaque — the client must
     * not build one — and the last page returns {@code null} for it.</p>
     *
     * <p>The {@code summary} block is unaffected by {@code cursor}: it describes
     * every event matching the filters, so it stays the same on each page.</p>
     *
     * @param userId    the user whose analytics to read, named in the path
     * @param eventType optional event name to narrow to; an unrecognised value
     *                  is rejected as a bad request
     * @param from      optional inclusive lower bound on event time, ISO-8601
     *                  (for example {@code 2026-10-01T00:00:00Z})
     * @param to        optional inclusive upper bound on event time, ISO-8601
     * @param cursor    the previous page's {@code nextCursor}; omit for the
     *                  first page
     * @param size      page size, 1 to {@value AdminAnalyticsQueryService#MAX_PAGE_SIZE};
     *                  defaults to {@value AdminAnalyticsQueryService#DEFAULT_PAGE_SIZE}
     * @param jwt       the authenticated administrator, recorded in the log
     * @return the page of events and the totals for the filtered set
     */
    @GetMapping("/users/{userId}/events")
    public ResponseEntity<AnalyticsUserEventsResponse> getUserEvents(
            @PathVariable UUID userId,
            @RequestParam(required = false) AnalyticsEventType eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "0") int size,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Administrator {} read analytics for user {} (eventType={}, from={}, to={}, size={})",
                jwt.getSubject(), userId, eventType, from, to, size);

        return ResponseEntity.ok(
                adminAnalyticsQueryService.getUserEvents(
                        userId,
                        eventType,
                        from,
                        to,
                        cursor,
                        size));
    }

    /**
     * Searches every user's events by event name, newest first.
     *
     * <p>The cross-user counterpart to {@link #getUserEvents}: same table, same
     * ordering, same cursor paging, but no user named in the path. Each item
     * carries the {@code userId} it belongs to, because a page here mixes
     * people.</p>
     *
     * <p>{@code eventType} is what makes the search selective. It is optional —
     * omitting it searches everything — but without it the query reads the whole
     * table rather than a range of it, so a client that knows which event it
     * wants should always send it. There is no summary block: totals across
     * every user would be a second unbounded scan behind a request that is only
     * meant to return a page.</p>
     *
     * @param eventType optional event name to search for; an unrecognised value
     *                  is rejected as a bad request
     * @param from      optional inclusive lower bound on event time, ISO-8601
     * @param to        optional inclusive upper bound on event time, ISO-8601
     * @param cursor    the previous page's {@code nextCursor}; omit for the
     *                  first page
     * @param size      page size, 1 to {@value AdminAnalyticsQueryService#MAX_PAGE_SIZE};
     *                  defaults to {@value AdminAnalyticsQueryService#DEFAULT_PAGE_SIZE}
     * @param jwt       the authenticated administrator, recorded in the log
     * @return the page of matching events, across all users
     */
    @GetMapping("/events")
    public ResponseEntity<AnalyticsEventSearchResponse> searchEvents(
            @RequestParam(required = false) AnalyticsEventType eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "0") int size,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Administrator {} searched analytics events (eventType={}, from={}, to={}, size={})",
                jwt.getSubject(), eventType, from, to, size);

        return ResponseEntity.ok(
                adminAnalyticsQueryService.searchEvents(
                        eventType,
                        from,
                        to,
                        cursor,
                        size));
    }
}
