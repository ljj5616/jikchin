package com.jikchin.jikchin_app.adapter.in.web.security;

import com.jikchin.jikchin_app.application.port.out.auth.TokenProviderPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAttributeFilter extends OncePerRequestFilter {

    private final TokenProviderPort tokenProviderPort;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        if (uri.matches("^/auth/(google|naver|kakao)/login$")) {
            filterChain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                var parsed = tokenProviderPort.parse(token);
                request.setAttribute("userId", parsed.userId());
                request.setAttribute("needProfile", parsed.needProfile());
                request.setAttribute("tokenType", parsed.typ());
            } catch (Exception e) {
                // 토큰이 있어도 잘못된 경우: 온보딩/보호 엔드포인트 접근이면 401 반환
                if (uri.equals("/auth/complete-profile")) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid or expired token");
                    return;
                }
                // 그 외는 그냥 통과시켜도 됨(공개 API라면)
            }
        } else {
            // 토큰 없음: 온보딩 엔드포인트 접근이면 401
            if (uri.equals("/auth/complete-profile")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "missing token");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
