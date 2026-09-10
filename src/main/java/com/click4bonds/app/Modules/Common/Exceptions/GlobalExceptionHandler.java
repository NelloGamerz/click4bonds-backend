package com.click4bonds.app.Modules.Common.Exceptions;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

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
         * Handles database constraint violations.
         *
         * This is particularly important for the unique email
         * constraint on contact inquiries.
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
                                                                "A contact request has already been submitted for this email address."));
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
