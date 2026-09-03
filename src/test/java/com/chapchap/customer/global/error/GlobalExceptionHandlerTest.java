package com.chapchap.customer.global.error;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.error.custom.faq.FaqNotFoundException;
import com.chapchap.customer.global.error.custom.knowledge.KnowledgeProcessingContractException;
import com.chapchap.customer.global.error.custom.knowledge.KnowledgeVersionStateException;
import com.chapchap.customer.global.response.GlobalResponse;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void handlesFeatureBusinessExceptionThroughBusinessParentHandler() {
        var response = globalExceptionHandler.handleBusinessException(new FaqNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .extracting(GlobalResponse::code)
                .isEqualTo(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR.getCode());
    }

    @Test
    void keepsBusinessStateFailureAndTechnicalContractFailureSeparated() {
        assertThat(new KnowledgeVersionStateException("invalid state"))
                .isInstanceOf(BusinessException.class);
        assertThat(new KnowledgeProcessingContractException("invalid contract"))
                .isNotInstanceOf(BusinessException.class);
    }
}
