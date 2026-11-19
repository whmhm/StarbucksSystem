package com.whm.starbucks.filter;

import com.whm.starbucks.config.AccessTokenBlacklist;
import com.whm.starbucks.config.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Authorization: Bearer xxx，校验通过后把认证信息写入 SecurityContext。
 * 只做「解析 + 认证」，是否放行由 SecurityConfig 的 authorizeRequests 决定。
 * <p>
 * 注意：本类不加 @Component，避免被 Spring Boot 注册成全局 Servlet 过滤器，
 * 而是在 SecurityConfig 里通过 addFilterBefore 精确插入安全过滤器链。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 无需解析 Access Token 的公开路径 */
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/auth/login", "/auth/refresh", "/user/register");

    private final JwtService jwtService;

    // 用于撤销 Access Token
    private final AccessTokenBlacklist accessTokenBlacklist;
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // 没有凭证：直接放行，由 Security 对需要认证的接口返回 401
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtService.parseToken(token);
            // Refresh Token 不能当访问凭证使用
            if (!jwtService.isAccessToken(claims)) {
                chain.doFilter(request, response);
                return;
            }
            if (accessTokenBlacklist.isRevoked(claims.getId())) {
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }

            Long userId = Long.valueOf(claims.getSubject());
            Integer memberLevel = readMemberLevel(claims);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, resolveAuthorities(memberLevel));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // 签名错误 / 已过期 / 格式非法：清空上下文，交给 Security 统一返回 401
            SecurityContextHolder.clearContext();
            log.debug("JWT 校验失败: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private Integer readMemberLevel(Claims claims) {
        Object raw = claims.get("memberLevel");
        return raw instanceof Number ? ((Number) raw).intValue() : null;
    }

    private List<GrantedAuthority> resolveAuthorities(Integer memberLevel) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (memberLevel != null && memberLevel > 0) {
            authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }
        return authorities;
    }
}
