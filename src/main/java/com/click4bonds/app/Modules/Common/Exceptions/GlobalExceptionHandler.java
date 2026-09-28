package com.click4bonds.app.Modules.Common.Exceptions;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.click4bonds.app.Modules.Auth.Exception.InvalidSessionException;
import com.click4bonds.app.Modules.Auth.Exception.OtpRateLimitedException;
import com.click4bonds.app.Modules.Common.Dto.ApiError;
import com.click4bonds.app.Modules.Email.Exception.EmailSendException;
import com.click4bonds.app.Modules.OTP.Exception.InvalidOtpException;
import com.click4bonds.app.Modules.OTP.Exception.OtpMaxAttemptsExceededException;
import com.click4bonds.app.Modules.OTP.Exception.OtpResendCooldownException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiError> handleNotFound(
                        ResourceNotFoundException ex) {

                log.warn("Resource not found: {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(
                                                new ApiError(
                                                                "NOT_FOUND",
                                                                ex.getMessage()));
        }

        @ExceptionHandler(BadRequestException.class)
        public ResponseEntity<ApiError> handleBadRequest(
                        BadRequestException ex) {

                log.warn("Bad request: {}", ex.getMessage());

                return ResponseEntity
                                .badRequest()
                                .body(
                                                new ApiError(
                                                                "BAD_REQUEST",
                                                                ex.getMessage()));
        }

        @ExceptionHandler(ConflictException.class)
        public ResponseEntity<ApiError> handleConflict(
                        ConflictException ex) {

                log.warn("Conflict: {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(
                                                new ApiError(
                                                                "CONFLICT",
                                                                ex.getMessage()));
        }

        @ExceptionHandler(ForbiddenException.class)
        public ResponseEntity<ApiError> handleForbidden(
                        ForbiddenException ex) {

                log.warn("Forbidden request: {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(
                                                new ApiError(
                                                                "FORBIDDEN",
                                                                ex.getMessage()));
        }

        /**
         * Handles a body that could not be read at all.
         *
         * <p>Reached before bean validation runs, so it covers what the
         * annotations cannot: JSON that is not well-formed, a body of the wrong
         * shape, and a value outside an enum — an unknown {@code ageRange}, for
         * instance, is rejected by the deserialiser rather than by a constraint.
         * Without this, all of those would fall through to the catch-all below
         * and be reported as a server fault when the fault is the caller's.</p>
         *
         * <p>The parser's own message is not passed on. It describes the
         * internal shape of the target type, which is not something the caller
         * asked about and not something to hand out.</p>
         */
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiError> handleUnreadableBody(
                        HttpMessageNotReadableException ex) {

                log.warn("Request body could not be read: {}", ex.getMessage());

                return ResponseEntity
                                .badRequest()
                                .body(
                                                new ApiError(
                                                                "MALFORMED_BODY",
                                                                "The request body could not be read. Check that every field is present and holds an allowed value."));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> handleValidation(
                        MethodArgumentNotValidException ex) {

                String message = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(error -> error.getField()
                                                + ": "
                                                + error.getDefaultMessage())
                                .collect(Collectors.joining(", "));

                log.warn("Validation failed: {}", message);

                return ResponseEntity
                                .badRequest()
                                .body(
                                                new ApiError(
                                                                "VALIDATION_ERROR",
                                                                message));
        }

        /**
         * Handles database constraint violations that no service turned into a
         * clearer answer of its own.
         *
         * <p>Worded without naming a table or a column, because it is a
         * fallback: the caller is told their details collide with something
         * that already exists, and nothing about the schema. A service that
         * knows which constraint it hit — the contact inquiry flow does —
         * catches the violation and says something more useful instead.</p>
         *
         * <p>Expected to be reached most often by two sign-ups racing for the
         * same phone number, where the unique index is the thing that actually
         * decides it.</p>
         */
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiError> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex) {

                log.warn(
                                "Database integrity violation: {}",
                                ex.getMostSpecificCause().getMessage());

                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(
                                                new ApiError(
                                                                "CONFLICT",
                                                                "These details conflict with a record that already exists."));
        }

        /**
         * Handles failures of the configured email provider.
         *
         * The provider's own error type and message are never exposed to the
         * caller; they are logged with the request.
         */
        @ExceptionHandler(EmailSendException.class)
        public ResponseEntity<ApiError> handleEmailSendFailure(
                        EmailSendException ex) {

                log.error(
                                "Email could not be sent: {}",
                                ex.getMessage(),
                                ex);

                return ResponseEntity
                                .status(HttpStatus.BAD_GATEWAY)
                                .body(
                                                new ApiError(
                                                                "EMAIL_SEND_FAILED",
                                                                "We could not send the email right now. Please try again later."));
        }

        /**
         * Handles a submitted OTP that is wrong, unknown or expired.
         *
         * <p>Declared explicitly on purpose. An {@code @ExceptionHandler} always
         * wins over the {@code @ResponseStatus} declared on the exception class,
         * so without this method — and the two below — the catch-all handler at
         * the bottom of this class would turn every OTP rejection into a 500.</p>
         */
        @ExceptionHandler(InvalidOtpException.class)
        public ResponseEntity<ApiError> handleInvalidOtp(
                        InvalidOtpException ex) {

                log.warn("OTP verification rejected: {}", ex.getMessage());

                return ResponseEntity
                                .badRequest()
                                .body(
                                                new ApiError(
                                                                "INVALID_OTP",
                                                                ex.getMessage()));
        }

        /**
         * Handles an OTP requested again before the resend cooldown elapsed.
         */
        @ExceptionHandler(OtpResendCooldownException.class)
        public ResponseEntity<ApiError> handleOtpResendCooldown(
                        OtpResendCooldownException ex) {

                log.warn("OTP request rejected: {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(
                                                new ApiError(
                                                                "OTP_RESEND_COOLDOWN",
                                                                ex.getMessage()));
        }

        /**
         * Handles an OTP destroyed after too many failed verifications.
         */
        @ExceptionHandler(OtpMaxAttemptsExceededException.class)
        public ResponseEntity<ApiError> handleOtpMaxAttempts(
                        OtpMaxAttemptsExceededException ex) {

                log.warn("OTP request rejected: {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(
                                                new ApiError(
                                                                "OTP_MAX_ATTEMPTS_EXCEEDED",
                                                                ex.getMessage()));
        }

        /**
         * Handles a session that could not be resolved.
         *
         * <p>Answers 401 rather than 403: the caller's credential is not good
         * enough, and the remedy is to sign in again — which is exactly what a
         * client should infer from this status. The same response covers an
         * absent cookie, an expired session and a revoked one, so it cannot be
         * used to probe whether a given session identifier exists.</p>
         */
        @ExceptionHandler(InvalidSessionException.class)
        public ResponseEntity<ApiError> handleInvalidSession(
                        InvalidSessionException ex) {

                log.debug("Session could not be resolved");

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(
                                                new ApiError(
                                                                "INVALID_SESSION",
                                                                ex.getMessage()));
        }

        /**
         * Handles an OTP request beyond the client address's allowance.
         */
        @ExceptionHandler(OtpRateLimitedException.class)
        public ResponseEntity<ApiError> handleOtpRateLimited(
                        OtpRateLimitedException ex) {

                log.warn("OTP request rejected: client rate limit reached");

                return ResponseEntity
                                .status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(
                                                new ApiError(
                                                                "OTP_RATE_LIMITED",
                                                                ex.getMessage()));
        }

        /**
         * Honours the status carried by a {@link ResponseStatusException}.
         *
         * <p>Without this, the catch-all handler would report a deliberately
         * raised 404 or 403 as a 500 — the same precedence rule described on
         * {@link #handleInvalidOtp} applies to these as well.</p>
         */
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiError> handleResponseStatus(
                        ResponseStatusException ex) {

                log.warn(
                                "Request rejected with {}: {}",
                                ex.getStatusCode(),
                                ex.getReason());

                HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());

                return ResponseEntity
                                .status(ex.getStatusCode())
                                .body(
                                                new ApiError(
                                                                status == null
                                                                                ? "ERROR"
                                                                                : status.name(),
                                                                ex.getReason() == null
                                                                                ? "Request could not be completed"
                                                                                : ex.getReason()));
        }

        /**
         * Handles unexpected internal application errors.
         */
        @ExceptionHandler(InternalServerException.class)
        public ResponseEntity<ApiError> handleInternalServerError(
                        InternalServerException ex) {

                log.error(
                                "Internal server error: {}",
                                ex.getMessage(),
                                ex);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                                new ApiError(
                                                                "INTERNAL_SERVER_ERROR",
                                                                ex.getMessage()));
        }

        /**
         * Fallback handler for unexpected exceptions.
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiError> handleUnexpectedException(
                        Exception ex) {

                log.error(
                                "Unexpected application error",
                                ex);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(
                                                new ApiError(
                                                                "INTERNAL_SERVER_ERROR",
                                                                "An unexpected error occurred. Please try again later."));
        }
}
