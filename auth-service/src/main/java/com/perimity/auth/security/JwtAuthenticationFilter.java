package com.perimity.auth.security;

import com.perimity.auth.service.TokenDenylistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the Bearer token and populates the security context.
 *
 * Silent on failure by design: a bad token leaves the context empty and Spring
 * Security answers 401 from its own entry point. Writing an error here would
 * produce two different 401 bodies for the same situation.
 *
 * The role is stored as ROLE_<NAME> so hasRole('FACULTY') works with no mapping.
 *
 * THIS FILE IS THE ONE THE OTHER FIVE SERVICES COPY. Only the package changes -
 * and, if they want logout to apply at their service too, the denylist check
 * below. Without it a logged-out token still opens their endpoints until it
 * expires. See the note in the logout README.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenDenylistService denylist;

    public JwtAuthenticationFilter(JwtService jwtService, TokenDenylistService denylist) {
        this.jwtService = jwtService;
        this.denylist = denylist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7).trim());

                /*
                 * Signature and expiry both passed, so this token is genuine -
                 * but it may have been logged out. Checked here rather than in
                 * a controller because every protected endpoint has to honour
                 * it, and one that forgot would be a silently working session
                 * after logout.
                 *
                 * Left silent on refusal, like a bad signature: the context
                 * stays empty and Spring Security answers its own 401. Saying
                 * "this token was logged out" would tell a holder of a stolen
                 * token exactly why it stopped working.
                 */
                if (denylist.isDenied(claims.getId())) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }

                PerimityPrincipal principal = PerimityPrincipal.from(claims);

                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
