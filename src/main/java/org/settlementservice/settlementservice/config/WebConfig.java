package org.settlementservice.settlementservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebConfig {
    // This configuration ensures stable JSON structure for paginated responses
    // Prevents the PageImpl serialization warning
}
