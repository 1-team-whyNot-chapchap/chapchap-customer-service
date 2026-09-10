package com.chapchap.customer.global.config.security;

import com.chapchap.customer.global.security.filter.CurrentAccountFilter;
import com.chapchap.customer.global.security.filter.GatewayUserContextFilter;
import com.chapchap.customer.global.security.filter.TraceIdFilter;

import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import com.chapchap.customer.domain.consultation.controller.summary.ConsultationSummaryCallbackController;
import com.chapchap.customer.domain.knowledge.controller.processing.async.KnowledgeProcessingCallbackController;
import com.chapchap.customer.domain.customerai.controller.security.CustomerAiSubjectJwksController;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 메소드 레벨 권한 제어 활성화
@RequiredArgsConstructor
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity httpSecurity,
            TraceIdFilter traceIdFilter,
            GatewayUserContextFilter gatewayUserContextFilter,
            CurrentAccountFilter currentAccountFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        return httpSecurity
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(gatewayUserContextFilter, TraceIdFilter.class)
                .addFilterAfter(currentAccountFilter, GatewayUserContextFilter.class)
                .authorizeHttpRequests(request -> request
                        // 최초 REQUEST에서 인증한 SSE의 완료 dispatch는 컨트롤러를 다시 호출하지 않는다.
                        .requestMatchers(dispatch -> dispatch.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC
                                && "/api/customer/notifications/stream".equals(dispatch.getRequestURI())).permitAll()
                        .requestMatchers(
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, CustomerAiSubjectJwksController.PATH).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                KnowledgeProcessingCallbackController.PATH,
                                ConsultationSummaryCallbackController.PATH
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/customer/faqs", "/api/customer/faqs/**").permitAll()
                        .requestMatchers("/ws/customer/consultations/**").authenticated()
                        .requestMatchers("/api/customer/**").authenticated()
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
