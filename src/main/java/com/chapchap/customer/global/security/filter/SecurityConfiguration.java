package com.chapchap.customer.global.security.filter;

import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 메소드 레벨 권한 제어 활성화
@RequiredArgsConstructor
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity httpSecurity,
            TraceIdFilter traceIdFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        return httpSecurity
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(request -> request
                        .requestMatchers(
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, CustomResponseCode.UNAUTHENTICATED_ERROR))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, CustomResponseCode.UNAUTHORIZED_ERROR)))
                .build();
    }

    private void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            CustomResponseCode responseCode
    ) throws java.io.IOException {
        response.setStatus(responseCode.getHttpStatus().value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), GlobalResponse.from(responseCode));
    }
}
