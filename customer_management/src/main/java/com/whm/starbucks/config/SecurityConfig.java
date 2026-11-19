package com.whm.starbucks.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whm.starbucks.filter.JwtAuthenticationFilter;
import com.whm.starbucks.pojo.vo.ResponseVo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 无状态 JWT 鉴权配置：
 * 1. 关闭 CSRF / formLogin / httpBasic / session，接口只认 Authorization: Bearer 头；
 * 2. 把 JwtAuthenticationFilter 插到 UsernamePasswordAuthenticationFilter 之前；
 * 3. 未登录返回 401、无权限返回 403，响应体统一为 ResponseVo（与 GlobalExceptionHandler 一致）。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // 无状态 JWT 接口不需要 CSRF，否则 curl 的 POST 会被拦成 403
        http.csrf().disable();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        http.formLogin().disable();
        http.httpBasic().disable();
        http.logout().disable();

        // 未认证 / 无权限时也返回统一 JSON，避免前端拿到 HTML 或空响应
        http.exceptionHandling()
                .authenticationEntryPoint((request, response, e) ->
                        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已失效"))
                .accessDeniedHandler((request, response, e) ->
                        writeError(response, HttpServletResponse.SC_FORBIDDEN, "没有访问权限"));

        http.authorizeRequests()
                .antMatchers("/auth/**").permitAll()
                .antMatchers("/user/register").permitAll()
                .anyRequest().authenticated();

        http.addFilterBefore(new JwtAuthenticationFilter(jwtService, accessTokenBlacklist),
                UsernamePasswordAuthenticationFilter.class);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ResponseVo.fail(status, message));
    }
}
