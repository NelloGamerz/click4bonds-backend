package com.click4bonds.app.Modules.Rcs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "rcs.api")
public class RcsApiProperties {

    private String baseUrl;
    private String apiKey;
    private String otpTemplateId;
}
