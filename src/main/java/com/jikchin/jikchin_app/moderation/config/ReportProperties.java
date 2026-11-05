package com.jikchin.jikchin_app.moderation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.reports")
@Getter
@Setter
public class ReportProperties {
    private boolean enabled = true;
}
