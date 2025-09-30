package com.jikchin.jikchin_app.adapter.out.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jikchin.jikchin_app.application.port.out.auth.OAuthProviderPort;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class OAuthProviderAdapter implements OAuthProviderPort {

    private final RestTemplate restTemplate;

    @Value("${oauth.google.client-id}")
    String googleClientId;


    @Override
    public String verifyAndGetProviderId(String provider, String accessToken) {
        String p = provider == null ? "" : provider.toLowerCase();
        return switch (p) {
            case "google" -> verifyGoogle(accessToken);
            case "naver" -> verifyNaver(accessToken);
            case "kakao" -> verifyKakao(accessToken);
            default -> throw new IllegalArgumentException("unsupported provider: " + provider);
        };
    }

    private String verifyGoogle(String token) {
        try {
            if (looksLikeIdToken(token)) {
                // ID 토큰 경로
                GoogleTokenInfo info = restTemplate.getForObject(
                        "https://oauth2.googleapis.com/tokeninfo?id_token={id}",
                        GoogleTokenInfo.class,
                        token
                );
                if (info == null || info.sub == null || info.aud == null || !info.aud.equals(googleClientId)) {
                    throw new IllegalArgumentException("invalid google id_token");
                }
                return info.sub; // provider user id
            } else {
                // Access Token 경로 (현재 코드와 동일)
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(token);
                headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<GoogleUserinfo> resp = restTemplate.exchange(
                        "https://www.googleapis.com/oauth2/v3/userinfo",
                        HttpMethod.GET, entity, GoogleUserinfo.class
                );
                GoogleUserinfo body = resp.getBody();
                if (body == null || body.sub == null) throw new IllegalArgumentException("invalid google access token");
                return body.sub;
            }
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("invalid google token", e);
        }
    }

    private boolean looksLikeIdToken(String token) {
        // 매우 단순한 판별: JWT 형태이면(ID 토큰) true
        return token != null && token.chars().filter(ch -> ch == '.').count() == 2;
    }

    private String verifyNaver(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<NaverResponse> resp = restTemplate.exchange(
                    "https://openapi.naver.com/v1/nid/me",
                    HttpMethod.GET,
                    entity,
                    NaverResponse.class
            );
            NaverResponse body = resp.getBody();
            if (body == null || !"00".equals(body.getResultcode()) || body.getResponse() == null)
                throw new IllegalArgumentException("invalid naver token");
            return body.getResponse().getId();
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("invalid naver token", e);
        }
    }

    private String verifyKakao(String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<KakaoUser> resp = restTemplate.exchange(
                    "https://kapi.kakao.com/v2/user/me",
                    HttpMethod.GET,
                    entity,
                    KakaoUser.class
            );
            KakaoUser body = resp.getBody();
            if (body == null || body.getId() == null) throw new IllegalArgumentException("invalid kakao token");
            return String.valueOf(body.getId());
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("invalid kakao token", e);
        }
    }

    // ====== response DTOs (간단 매핑용) ======
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data static final class GoogleUserinfo { private String sub; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data static final class GoogleTokenInfo { private String sub; private String aud; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data static final class NaverResponse { private String resultcode; private String message; private NaverUser response; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data static final class NaverUser { private String id; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data static final class KakaoUser { private Long id; }
}