package com.jikchin.jikchin_app.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000); // 5s
        factory.setReadTimeout(10_000);   // 10s

        return builder
                .requestFactory(() -> factory) // 타임아웃이 박힌 팩토리를 사용
                .build();
    }
}
