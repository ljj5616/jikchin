package com.jikchin.jikchin_app.adapter.in.web.security;

import com.jikchin.jikchin_app.application.port.out.auth.TokenProviderPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenProviderPort tokenProviderPort;


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1) JwtAttributeFilter가 이미 세팅한 값이 있으면 재사용
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            boolean needProfile = request.getAttribute("needProfile") instanceof Boolean b && b;
            String tokenType = request.getAttribute("tokenType") != null
                    ? request.getAttribute("tokenType").toString()
                    : null;

            CurrentUser principal = new CurrentUser(Long.valueOf(userIdAttr.toString()), needProfile, tokenType);
            setAuth(principal);
            filterChain.doFilter(request, response);
            return;
        }

        // 2) 없으면 Authorization 헤더에서 직접 파싱
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            try {
                var parsed = tokenProviderPort.parse(token);
                CurrentUser principal = new CurrentUser(parsed.userId(), parsed.needProfile(), parsed.typ());
                setAuth(principal);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setAuth(CurrentUser principal) {
        // 필요하면 권한도 부여 가능:
        // var authorities = principal.needProfile()
        //         ? List.of(new SimpleGrantedAuthority("ROLE_ONBOARDING"))
        //         : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.NO_AUTHORITIES // 위 주석처럼 roles 쓰면 여기 바꿔줘
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
