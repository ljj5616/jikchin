package com.jikchin.jikchin_app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("직친 API")
                        .description("테스트용 OpenAPI 문서")
                        .version("v1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    /**
     * /auth/** 그룹 (로그인/회원가입 등)
     * - 이 그룹 안의 엔드포인트는 문서상에서 따로 모아 보여줌
     * - 아래 customizer로 /auth/** 는 전역 보안 제거(Authorize 없이 호출 가능) 처리
     */
    @Bean
    public GroupedOpenApi authGroup(OpenApiCustomizer authOpenApiCustomizer) {
        return GroupedOpenApi.builder()
                .group("auth")
                .pathsToMatch("/auth/**")
                .addOpenApiCustomizer(authOpenApiCustomizer)
                .build();
    }

    @Bean
    public OpenApiCustomizer authOpenApiCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (path.startsWith("/auth/")) {
                item.readOperations().forEach(op -> op.setSecurity(List.of())); // 보안 요구 제거
            }
        });
    }

    /**
     * 일반 v1 API 그룹 - 예: /api/** 아래 도메인 API들
     */
    @Bean
    public GroupedOpenApi v1Group() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/api/**")
                .build();
    }
}
