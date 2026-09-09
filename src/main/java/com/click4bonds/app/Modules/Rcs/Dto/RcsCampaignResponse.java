package com.click4bonds.app.Modules.Rcs.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class RcsCampaignResponse {

    @JsonProperty("Status")
    private String status;

    @JsonProperty("Response")
    private ResponseData response;

    @Data
    public static class ResponseData {

        @JsonProperty("Message")
        private String message;

        @JsonProperty("CampaignId")
        private Long campaignId;

        @JsonProperty("TotalMobiles")
        private Integer totalMobiles;
    }
}
