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

import com.click4bonds.app.Modules.Common.Dto.ApiError;

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
