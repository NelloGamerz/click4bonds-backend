package com.click4bonds.app.Modules.Rcs.Service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.click4bonds.app.Modules.Rcs.Dto.RcsCampaignRequest;
import com.click4bonds.app.Modules.Rcs.Dto.RcsCampaignResponse;
import com.click4bonds.app.Modules.Rcs.config.RcsApiProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RcsMessageService {

    private final RestClient restClient;
    private final RcsApiProperties rcsApiProperties;

    public RcsCampaignResponse sendOtp(String mobileNumber, String otp) {

        RcsCampaignRequest request = RcsCampaignRequest.builder()
                .templateId(rcsApiProperties.getOtpTemplateId())
                .campaignName("OTP_Verification")
                .mobileNumbers(List.of(mobileNumber + "," + otp))
                .enableFallback(false)
                .build();

        // return restClient.post()
        // .uri(uriBuilder -> uriBuilder
        // .path(rcsApiProperties.getBaseUrl() + "/CreateCampaign")
        // .queryParam("apiKey", rcsApiProperties.getApiKey())
        // .build())
        // .body(request)
        // .retrieve()
        // .body(RcsCampaignResponse.class);

        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("biz.sms4power.com")
                        .path("/api/RCSApi/CreateCampaign")
                        .queryParam("apiKey", rcsApiProperties.getApiKey())
                        .build())
                .body(request)
                .retrieve()
                .body(RcsCampaignResponse.class); 
    }
}
