package com.click4bonds.app.Modules.User.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.User.Dto.SendEmailOtpRequest;
import com.click4bonds.app.Modules.User.Dto.SendPhoneOtpRequest;
import com.click4bonds.app.Modules.User.Dto.VerificationResponse;
import com.click4bonds.app.Modules.User.Dto.VerifyEmailOtpRequest;
import com.click4bonds.app.Modules.User.Dto.VerifyPhoneOtpRequest;
import com.click4bonds.app.Modules.User.Service.VerificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Email and phone verification for the authenticated user.
 *
 * <p>The user is always taken from the access token's subject — never from the
 * request body — so a caller cannot ask for somebody else's identifier to be
 * verified. The address or number in the body only ever narrows the request to
 * something the account must already own.</p>
 *
 * <p>No response from this controller contains an OTP.</p>
 */
@RestController
@RequestMapping("/api/users/verification")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "User verification", description = "Email and phone verification for the signed-in user")
public class UserVerificationController {

    private final VerificationService verificationService;

    @Operation(
            summary = "Send an email verification OTP",
            description = """
                    Issues a code for the signed-in user's own email address and delivers it
                    through the configured email provider. The code is subject to the OTP
                    module's resend cooldown and expiry, and is never returned in the response.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code issued, or the email was already verified"),
            @ApiResponse(responseCode = "400", description = "Malformed email address"),
            @ApiResponse(responseCode = "403", description = "The address is not the one on this account"),
            @ApiResponse(responseCode = "429", description = "A code was requested too soon after the previous one")
    })
    @PostMapping("/email/send-otp")
    public ResponseEntity<VerificationResponse> sendEmailOtp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendEmailOtpRequest request) {

        return ResponseEntity.ok(
                verificationService.sendEmailOtp(
                        jwt.getSubject(),
                        request.email()));
    }

    @Operation(
            summary = "Verify an email OTP",
            description = """
                    Consumes the code and, when it is accepted, marks the email as verified and
                    moves the account on to the phone verification step. A rejected code changes
                    nothing.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified"),
            @ApiResponse(responseCode = "400", description = "Malformed address or code, or the code is wrong or expired"),
            @ApiResponse(responseCode = "403", description = "The address is not the one on this account"),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts; request a new code")
    })
    @PostMapping("/email/verify")
    public ResponseEntity<VerificationResponse> verifyEmailOtp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyEmailOtpRequest request) {

        return ResponseEntity.ok(
                verificationService.verifyEmailOtp(
                        jwt.getSubject(),
                        request.email(),
                        request.otp()));
    }

    @Operation(
            summary = "Generate a phone verification OTP",
            description = """
                    Issues a code for a phone number belonging to the signed-in user. SMS
                    delivery is not implemented yet, so the code is generated and stored but
                    not sent; it is never returned in the response either.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code generated, or the number was already verified"),
            @ApiResponse(responseCode = "400", description = "Malformed phone number"),
            @ApiResponse(responseCode = "403", description = "The number is not the one on this account"),
            @ApiResponse(responseCode = "409", description = "The number is already registered to another account"),
            @ApiResponse(responseCode = "429", description = "A code was requested too soon after the previous one")
    })
    @PostMapping("/phone/send-otp")
    public ResponseEntity<VerificationResponse> sendPhoneOtp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendPhoneOtpRequest request) {

        return ResponseEntity.ok(
                verificationService.sendPhoneOtp(
                        jwt.getSubject(),
                        request.phone()));
    }

    @Operation(
            summary = "Verify a phone OTP",
            description = """
                    Consumes the code and, when it is accepted, binds the number to the account,
                    marks the phone as verified and moves onboarding on to the PAN step. A
                    rejected code changes nothing.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Phone number verified"),
            @ApiResponse(responseCode = "400", description = "Malformed number or code, or the code is wrong or expired"),
            @ApiResponse(responseCode = "403", description = "The number is not the one on this account"),
            @ApiResponse(responseCode = "409", description = "The number is already registered to another account"),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts; request a new code")
    })
    @PostMapping("/phone/verify")
    public ResponseEntity<VerificationResponse> verifyPhoneOtp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyPhoneOtpRequest request) {

        return ResponseEntity.ok(
                verificationService.verifyPhoneOtp(
                        jwt.getSubject(),
                        request.phone(),
                        request.otp()));
    }
}
