package com.click4bonds.app.Modules.Auth.Controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Auth.Dto.AuthResponse;
import com.click4bonds.app.Modules.Auth.Service.AuthCookieService;
import com.click4bonds.app.Modules.Auth.Service.AuthService;
import com.click4bonds.app.Modules.User.Dto.SendPhoneOtpRequest;
import com.click4bonds.app.Modules.User.Dto.UserResponse;
import com.click4bonds.app.Modules.User.Dto.VerificationResponse;
import com.click4bonds.app.Modules.User.Dto.VerifyPhoneOtpRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Signing in with a phone number.
 *
 * <p>Sign-in is two calls: one to have a code sent to a number, one to redeem
 * it. The second call is what creates the session, and it returns both an
 * access token and a cookie — the token for the {@code Authorization} header,
 * the cookie for the refresh and logout endpoints.</p>
 *
 * <p>The session identifier appears only in that cookie. It is never in a
 * response body, which is the point of the cookie being {@code HttpOnly}:
 * nothing running in the browser can read it, so it cannot be copied into
 * storage where it would outlive its usefulness.</p>
 *
 * <p>The first three endpoints are reachable without a token — they are how a
 * caller gets one. Refresh and logout authenticate by cookie instead, which is
 * why they do not require a token either. {@code /auth/me} is the exception: it
 * reads the account the access token names, so it needs the token.</p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Phone number and OTP sign-in")
public class AuthController {

    /** Reported for every send request, whether or not the number is known. */
    public static final String OTP_SENT =
            "If the number can be used to sign in, a verification code has been sent.";

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @Operation(
            summary = "Send a sign-in code by SMS",
            description = """
                    Issues a code for the given number and delivers it by SMS. The response is
                    the same whether or not an account exists for the number, so it cannot be
                    used to find out who is registered.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code issued"),
            @ApiResponse(responseCode = "400", description = "Malformed phone number"),
            @ApiResponse(responseCode = "429", description = "Too many requests from this client, or a code was requested too recently")
    })
    @PostMapping("/phone/send-otp")
    public ResponseEntity<VerificationResponse> sendPhoneOtp(
            @Valid @RequestBody SendPhoneOtpRequest request,
            HttpServletRequest httpRequest) {

        authService.sendPhoneOtp(
                request.phone(),
                clientAddress(httpRequest));

        return ResponseEntity.ok(new VerificationResponse(OTP_SENT));
    }

    @Operation(
            summary = "Verify a code and sign in",
            description = """
                    Redeems the code, creating the account if the number has never been used
                    before, and returns an access token. The session is set as an HttpOnly
                    cookie and is not included in the response body.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in"),
            @ApiResponse(responseCode = "400", description = "Malformed number or code, or the code is wrong or expired"),
            @ApiResponse(responseCode = "403", description = "The account may not sign in"),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts; request a new code")
    })
    @PostMapping("/phone/verify-otp")
    public ResponseEntity<AuthResponse> verifyPhoneOtp(
            @Valid @RequestBody VerifyPhoneOtpRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        AuthService.IssuedSession issued = authService.verifyPhoneOtp(
                request.phone(),
                request.otp(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));

        setSessionCookie(httpResponse, issued.sessionId());

        return ResponseEntity.ok(issued.response());
    }

    @Operation(
            summary = "Get the signed-in user's profile",
            description = """
                    Returns the full profile of the account the access token belongs to. The
                    verification record is included while KYC is outstanding and omitted once it
                    is complete.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The profile"),
            @ApiResponse(responseCode = "401", description = "No token, or it has expired or is invalid")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(authService.getProfile(jwt.getSubject()));
    }

    @Operation(
            summary = "Exchange the session cookie for a new access token",
            description = """
                    Reads the session cookie and returns a fresh access token. The session
                    rotates: the response carries a new cookie, which the browser stores in
                    place of the old one.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token issued"),
            @ApiResponse(responseCode = "401", description = "No session, or it has expired or been revoked")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        AuthService.IssuedSession issued = authService.refresh(
                authCookieService.readSessionId(httpRequest).orElse(null),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));

        setSessionCookie(httpResponse, issued.sessionId());

        return ResponseEntity.ok(issued.response());
    }

    @Operation(
            summary = "Sign out",
            description = """
                    Revokes the session and clears the cookie. Idempotent: calling it without a
                    session, or twice, still succeeds. Other devices stay signed in.
                    """)
    @ApiResponse(responseCode = "204", description = "Signed out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        authCookieService.readSessionId(httpRequest)
                .ifPresent(authService::logout);

        // Cleared even when there was no session, so a stale cookie from an
        // already-expired session is not left behind in the browser.
        httpResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                authCookieService.clearCookie());

        return ResponseEntity.noContent().build();
    }

    private void setSessionCookie(HttpServletResponse httpResponse, String sessionId) {

        httpResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                authCookieService.issueCookie(sessionId));
    }

    /**
     * @return the address the rate limit counts against.
     *
     *         <p>Behind a load balancer this is only the real client when the
     *         deployment tells the container to read the forwarded headers —
     *         {@code server.forward-headers-strategy=framework}. Without that
     *         every request appears to come from the proxy and the limit is
     *         shared by everybody, which is a throttle rather than a defence.
     *         Trusting the header from the request instead would be worse: it
     *         is caller-supplied, so it would let the limit be bypassed at
     *         will.</p>
     */
    private String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
