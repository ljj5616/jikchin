package com.jikchin.jikchin_app.adapter.out.oauth;

import com.jikchin.jikchin_app.application.port.out.auth.OAuthProviderPort;
import lombok.Data;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class OAuthProviderAdapter implements OAuthProviderPort {

    private final RestTemplate restTemplate = new RestTemplate();

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
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<GoogleUserinfo> resp = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    HttpMethod.GET,
                    entity,
                    GoogleUserinfo.class
            );
            GoogleUserinfo body = resp.getBody();
            if (body == null || body.getSub() == null) throw new IllegalArgumentException("invalid google token");
            return body.getSub();
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("invalid google token", e);
        }
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
    @Data static class GoogleUserinfo { private String sub; }
    @Data static class NaverResponse { private String resultcode; private String message; private NaverUser response; }
    @Data static class NaverUser { private String id; }
    @Data static class KakaoUser { private Long id; }
}
