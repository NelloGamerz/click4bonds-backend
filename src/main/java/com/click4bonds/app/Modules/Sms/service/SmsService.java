package com.click4bonds.app.Modules.Sms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Service
public class SmsService {

    private final RestClient restClient;

    private final String baseUrl;
    private final String ukey;
    private final String senderId;
    private final String otpMessage;

    public SmsService(RestClient restClient,
                      @Value("${sms.gateway.base-url}") String baseUrl,
                      @Value("${sms.gateway.ukey}") String ukey,
                      @Value("${sms.gateway.sender-id}") String senderId,
                      @Value("${sms.gateway.otp-message}") String otpMessage) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.ukey = ukey;
        this.senderId = senderId;
        this.otpMessage = otpMessage;
    }

    public void sendOtp(String phone, String otp) {
        String message = otpMessage.replace("<arg1>", otp);

        try {
            String response = restClient.get()
                    .uri(baseUrl
                                    + "?ukey={ukey}&msisdn={msisdn}&language=0&credittype=7"
                                    + "&senderid={senderid}&templateid=0&message={message}&filetype=2",
                            Map.of(
                                    "ukey", ukey,
                                    "msisdn", toMsisdn(phone),
                                    "senderid", senderId,
                                    "message", message))
                    .retrieve()
                    .body(String.class);

            log.debug("SMS gateway response: {}", response);
        } catch (RestClientException e) {
            log.error("Failed to send OTP SMS", e);
            throw new IllegalStateException("Unable to send OTP right now, please try again");
        }
    }

    // Gateway example uses a 10-digit number (999950XXXX), so drop any +91 / 91 prefix
    private String toMsisdn(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }
}