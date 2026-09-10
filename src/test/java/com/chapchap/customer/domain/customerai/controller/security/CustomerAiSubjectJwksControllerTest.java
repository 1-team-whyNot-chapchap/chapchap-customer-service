package com.chapchap.customer.domain.customerai.controller.security;

import com.chapchap.customer.domain.customerai.response.security.CustomerAiSubjectJwksDocument;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerAiSubjectJwksControllerTest {
    @Test
    void publishesOnlyTheSubjectAssertionPublicJwk() throws Exception {
        CustomerAiSubjectJwksDocument document = new CustomerAiSubjectJwksDocument(List.of(
                new CustomerAiSubjectJwksDocument.Jwk(
                        "RSA", "sig", "RS256", "customer-subject-1", "modulus", "AQAB")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new CustomerAiSubjectJwksController(document)).build();

        mockMvc.perform(get(CustomerAiSubjectJwksController.PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("customer-subject-1"))
                .andExpect(jsonPath("$.keys[0].n").value("modulus"))
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }
}
