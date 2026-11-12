package com.jikchin.jikchin_app.auth.config;

import com.jikchin.jikchin_app.auth.security.JwtAuthenticationFilter;
import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenProviderPort tokenProviderPort;
    private final UserRepository userRepository;

    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthFilter = new JwtAuthenticationFilter(tokenProviderPort, userRepository);

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        // The STOMP CONNECT frame authenticates the WebSocket session.
                        .requestMatchers(HttpMethod.GET, "/ws/chat").permitAll()
                        .requestMatchers("/auth/google/login", "/auth/naver/login", "/auth/kakao/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers("/auth/complete-profile", "/auth/nickname-availability", "/auth/avatar").hasRole("ONBOARDING")
                        .requestMatchers(HttpMethod.GET, "/api/teams").hasAnyRole("ONBOARDING", "USER")
                        .requestMatchers("/auth/session").authenticated()
                        .anyRequest().hasRole("USER")
                )
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, exception) -> response.sendError(401)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
