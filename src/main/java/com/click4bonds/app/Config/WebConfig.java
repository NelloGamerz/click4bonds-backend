package com.click4bonds.app.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.click4bonds.app.Modules.Admin.Config.AuditLogInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableSpringDataWebSupport(
    pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO
)
public class WebConfig implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(auditLogInterceptor)
                .addPathPatterns("/api/admin/**")
                // Listing users is a bulk read, not an action on an account.
                // A particular account's details ({id}/details) still pass
                // through; user creation is recorded by AdminUserService.
                .excludePathPatterns("/api/admin/users");
    }
}
