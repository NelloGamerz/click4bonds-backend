package com.click4bonds.app.Modules.Admin.Config;

import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.click4bonds.app.Modules.Admin.Service.AuditLogService;
import com.click4bonds.app.Modules.User.Model.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Records every hit on a staff API, so no endpoint depends on a service
 * remembering to audit itself.
 *
 * <p>This covers the calls that are only a read or that have no dedicated
 * service entry — a listing, a preview, a document download. Actions whose
 * content matters (who was created, which role changed) are recorded explicitly
 * by their service, and this interceptor then skips the request so one action
 * does not produce two rows.</p>
 */
@Component
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = "audit.startTime";

    private final AuditLogService auditLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        if (AuditLogService.currentRequestAlreadyRecorded()) {
            // shortcut: an explicit entry that rolled back with its business
            // transaction leaves this request without any row; move the writer to
            // REQUIRES_NEW if audit of failed attempts is required.
            return;
        }

        User actor = auditLogService.currentActor();

        if (actor == null) {
            return;
        }

        Object startedAt = request.getAttribute(START_TIME_ATTRIBUTE);
        long durationMs = startedAt instanceof Long start
                ? System.currentTimeMillis() - start
                : 0L;

        String details = String.format(
                "%s %s -> %d (%d ms)",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                durationMs);

        auditLogService.write(
                actor,
                request.getMethod(),
                entityType(request.getRequestURI()),
                null,
                details);
    }

    /**
     * The part of the path after {@code /api/admin/}, uppercased: {@code USERS}
     * for a user call, {@code BONDS} for a bond call.
     */
    private static String entityType(String uri) {

        int index = uri.indexOf("/api/admin/");

        if (index < 0) {
            return "API";
        }

        String remainder = uri.substring(index + "/api/admin/".length());
        int slash = remainder.indexOf('/');
        String segment = slash < 0 ? remainder : remainder.substring(0, slash);

        return segment.isEmpty() ? "API" : segment.toUpperCase(Locale.ROOT);
    }
}
