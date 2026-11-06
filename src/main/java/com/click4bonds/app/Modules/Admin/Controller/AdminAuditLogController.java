package com.click4bonds.app.Modules.Admin.Controller;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Admin.Dto.AuditLogResponse;
import com.click4bonds.app.Modules.Admin.Service.AuditLogService;
import com.click4bonds.app.Modules.Common.Web.PaginationSorts;

import lombok.RequiredArgsConstructor;

/**
 * Reads the audit trail of one staff account.
 *
 * <p>Gated by {@code LOG_MANAGEMENT}: an {@code ADMIN} passes the module gate for
 * everything, and an {@code EMPLOYEE} only when that grant is on their profile.
 * The role gate is restated on the method because a method-level
 * {@code @PreAuthorize} replaces the class-level one rather than narrowing it.</p>
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
public class AdminAuditLogController {

    /** Columns the viewer may sort by. */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "createdAt",
            "action",
            "entityType");

    /** Newest first — the viewer is usually looking at what just happened. */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AuditLogService auditLogService;

    /**
     * One page of the actions a staff account performed.
     *
     * <p>With neither {@code from} nor {@code to} the answer covers the last 24
     * hours. A supplied bound completes the range with the other: {@code from}
     * alone runs to now, and {@code to} alone starts 24 hours before it. Both
     * are ISO-8601 instants, e.g. {@code 2026-11-05T10:15:30Z}.</p>
     *
     * @param userId   the staff account whose actions are being read
     * @param from     start of the range, inclusive; optional
     * @param to       end of the range, inclusive; optional
     * @param pageable page number, size and sort
     * @return the matching entries, newest first by default
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE') and @adminPermissionService.hasModule(authentication, 'LOG_MANAGEMENT')")
    public ResponseEntity<Page<AuditLogResponse>> getLogs(
            @RequestParam UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                auditLogService.getLogs(
                        userId,
                        from,
                        to,
                        PaginationSorts.restrict(
                                pageable,
                                SORTABLE_PROPERTIES,
                                DEFAULT_SORT)));
    }
}
