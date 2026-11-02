package com.jikchin.jikchin_app.auth;

import com.jikchin.jikchin_app.auth.infrastructure.oauth.OAuthProviderAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OAuthProviderAdapterTest {
    private OAuthProviderAdapter adapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.createServer(rest);
        adapter = new OAuthProviderAdapter(rest);
        ReflectionTestUtils.setField(adapter, "googleClientId", "my-client-id");
    }

    @Test
    void googleIdTokenMustBeIssuedForThisApplication() {
        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?id_token=a.b.c"))
                .andRespond(withSuccess("{\"sub\":\"google-user\",\"aud\":\"other-client\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.verifyAndGetProviderId("google", "a.b.c"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void googleIdTokenReturnsVerifiedSubject() {
        server.expect(requestTo("https://oauth2.googleapis.com/tokeninfo?id_token=a.b.c"))
                .andRespond(withSuccess("{\"sub\":\"google-user\",\"aud\":\"my-client-id\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.verifyAndGetProviderId("google", "a.b.c")).isEqualTo("google-user");
        server.verify();
    }

    @Test
    void naverRequiresAnIdentityInSuccessfulResponse() {
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer social-token"))
                .andRespond(withSuccess("{\"resultcode\":\"00\",\"response\":{}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.verifyAndGetProviderId("naver", "social-token"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
