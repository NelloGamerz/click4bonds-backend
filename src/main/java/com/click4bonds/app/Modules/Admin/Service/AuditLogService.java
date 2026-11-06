package com.click4bonds.app.Modules.Admin.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.click4bonds.app.Modules.Admin.Dto.AuditLogResponse;
import com.click4bonds.app.Modules.Admin.Model.AuditLog;
import com.click4bonds.app.Modules.Admin.Repository.AuditLogRepository;
import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;
import com.click4bonds.app.Modules.User.Enums.UserRole;
import com.click4bonds.app.Modules.User.Model.User;
import com.click4bonds.app.Modules.User.Service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes {@link AuditLog} entries for what a member of staff did.
 *
 * <p>The actor is taken from the request's token rather than passed in by every
 * caller: all staff endpoints are authenticated, so the security context
 * already knows who is acting, and threading that id through each service would
 * be the same fact restated at every call site.</p>
 *
 * <p>{@link #record} is for actions whose <em>content</em> matters — who was
 * created, which role changed to what. Endpoints that do not log explicitly are
 * still audited generically by
 * {@link com.click4bonds.app.Modules.Admin.Config.AuditLogInterceptor}. An
 * explicit entry marks the request so the interceptor does not write a second,
 * poorer row for the same request.</p>
 */
@Service
@Slf4j
public class AuditLogService {

    /**
     * Request attribute set once an explicit entry has been written, so the
     * interceptor skips its generic row for that request.
     */
    public static final String RECORDED_ATTRIBUTE = "audit.recorded";

    private final AuditLogRepository auditLogRepository;
    private final UserService userService;

    /*
     * UserService (through PartyService) reaches the PAN/bank/demat services,
     * which now audit too. Lazy breaks that construction cycle.
     */
    public AuditLogService(
            AuditLogRepository auditLogRepository,
            @Lazy UserService userService) {

        this.auditLogRepository = auditLogRepository;
        this.userService = userService;
    }

    /**
     * Records an action performed by the authenticated caller.
     *
     * <p>No-op outside an authenticated staff request — there is no actor to
     * attribute it to — and on any persistence failure, which is logged rather
     * than allowed to turn a completed business action into a 500.</p>
     */
    public void record(String action, String entityType, UUID entityId, String details) {

        User actor = currentActor();

        if (actor == null) {
            return;
        }

        write(actor, action, entityType, entityId, details);
        markRecorded();
    }

    /**
     * One page of a staff account's audit entries.
     *
     * <p>A range with neither bound means the last 24 hours; one supplied bound
     * is completed with the other (the window ends now, or starts 24 hours
     * before the given end).</p>
     *
     * @param userId   the staff account whose actions are being read
     * @param from     start of the range, inclusive; null means 24 hours before
     *                 {@code to}
     * @param to       end of the range, inclusive; null means now
     * @param pageable page number, size and sort
     * @return the matching entries
     * @throws BadRequestException when {@code from} is after {@code to}
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getLogs(
            UUID userId,
            Instant from,
            Instant to,
            Pageable pageable) {

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(24, ChronoUnit.HOURS);

        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must be before 'to'");
        }

        return auditLogRepository
                .findByPerformedBy_IdAndCreatedAtBetween(userId, start, end, pageable)
                .map(AuditLogResponse::from);
    }

    /**
     * Writes one entry. Used by the interceptor too, which knows the actor it
     * resolved but must not mark the request as already recorded.
     */
    public void write(User actor, String action, String entityType, UUID entityId, String details) {

        try {
            auditLogRepository.save(AuditLog.builder()
                    .performedBy(actor)
                    .performedByRole(actor.getRole())
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .ipAddress(currentIpAddress())
                    .build());

        } catch (RuntimeException ex) {
            // Auditing must never fail the request it is recording.
            log.warn("Could not write audit log entry for {} {}", action, entityType, ex);
        }
    }

    /**
     * The staff account behind the current request, or null when the request is
     * unauthenticated or not made by a member of staff.
     */
    public User currentActor() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        try {
            User user = userService
                    .findUser(UUID.fromString(jwt.getSubject()))
                    .orElse(null);

            return user != null && user.getRole() != UserRole.CUSTOMER ? user : null;

        } catch (RuntimeException ex) {
            log.debug("Could not resolve audit actor", ex);
            return null;
        }
    }

    public static boolean currentRequestAlreadyRecorded() {

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        return attributes != null
                && Boolean.TRUE.equals(
                        attributes.getAttribute(RECORDED_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }

    private static void markRecorded() {

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            attributes.setAttribute(RECORDED_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    private static String currentIpAddress() {

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return request.getRemoteAddr();
        }

        return null;
    }
}
