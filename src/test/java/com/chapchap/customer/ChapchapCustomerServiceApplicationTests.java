package com.chapchap.customer;

import com.chapchap.customer.domain.audit.repository.AuditLogRepository;
import com.chapchap.customer.domain.faq.repository.FaqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.security.Principal;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = "springdoc.api-docs.path=/api-docs")
@Import(ChapchapCustomerServiceApplicationTests.SecurityProbeConfiguration.class)
class ChapchapCustomerServiceApplicationTests {
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void rejectsCustomerRequestWithoutTrustedUserHeaders() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/authenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));
    }

    @Test
    void rejectsRequestWithOnlyOneTrustedUserHeader() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, "customer-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));

        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ROLE_HEADER, "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));
    }

    @Test
    void rejectsUnsupportedRole() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, "customer-1")
                        .header(USER_ROLE_HEADER, "UNKNOWN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));
    }

    @Test
    void rejectsBlankUserId() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, " ")
                        .header(USER_ROLE_HEADER, "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));
    }

    @Test
    void rejectsNonNumericUserId() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, "customer-1")
                        .header(USER_ROLE_HEADER, "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));
    }

    @Test
    void rejectsNonPositiveUserId() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, "0")
                        .header(USER_ROLE_HEADER, "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));

        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, "-1")
                        .header(USER_ROLE_HEADER, "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));
    }

    @Test
    void rejectsMultipleValuesForTrustedHeaders() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, "1", "2")
                        .header(USER_ROLE_HEADER, "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));

        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "CUSTOMER", "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "RIDER", "ADMIN", "SUPER_ADMIN"})
    void acceptsEveryGatewayRole(String role) throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/authenticated")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, role))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void rejectsNonAdminRoleFromAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/admin")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("E04"));
    }

    @Test
    void allowsAdminRoleToAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/admin")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsSuperAdminRoleToAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/security-probe/admin")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsAnonymousRequestToPublicFaqEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void doesNotAllowAnonymousWriteRequestOnPublicFaqPath() throws Exception {
        mockMvc.perform(post("/api/customer/faqs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E03"));
    }

    @Test
    void publishesFaqApiInOpenApiDocument() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/customer/faqs")));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityProbeConfiguration {
        @Bean
        SecurityProbeController securityProbeController() {
            return new SecurityProbeController();
        }

        @Bean
        FaqRepository faqRepository() {
            return mock(FaqRepository.class);
        }

        @Bean
        AuditLogRepository auditLogRepository() {
            return mock(AuditLogRepository.class);
        }
    }

    @RestController
    @RequestMapping(value = "/api/customer/security-probe", produces = MediaType.TEXT_PLAIN_VALUE)
    static class SecurityProbeController {
        @GetMapping("/authenticated")
        String authenticated(Principal principal) {
            return principal.getName();
        }

        @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
        @GetMapping("/admin")
        String admin() {
            return "admin";
        }
    }

}
