package com.click4bonds.app.Modules.Rcs.Dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RcsCampaignRequest {

    @JsonProperty("TemplateId")
    private String templateId;

    @JsonProperty("CampaignName")
    private String campaignName;

    @JsonProperty("MobileNumbers")
    private List<String> mobileNumbers;

    @JsonProperty("EnableFallback")
    private Boolean enableFallback;
}
