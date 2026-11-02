package com.jikchin.jikchin_app.auth.security;

import com.jikchin.jikchin_app.auth.port.TokenProviderPort;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenProviderPort tokens;
    private final UserRepository users;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var parsed = tokens.parse(header.substring(7));
                if ("access".equals(parsed.typ())) {
                    var user = users.findById(parsed.userId()).orElse(null);
                    UserStatus expected = parsed.needProfile() ? UserStatus.REGISTERING : UserStatus.ACTIVE;
                    if (user != null && user.getStatus() == expected) {
                        var principal = new CurrentUser(parsed.userId(), parsed.needProfile(), parsed.typ());
                        var roles = AuthorityUtils.createAuthorityList(parsed.needProfile() ? "ROLE_ONBOARDING" : "ROLE_USER");
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, roles));
                    }
                }
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
