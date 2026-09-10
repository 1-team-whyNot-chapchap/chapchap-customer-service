package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.dto.processing.async.CustomerAiKnowledgeJobCommand;
import com.chapchap.customer.domain.knowledge.response.processing.async.CustomerAiKnowledgeJobAccepted;

@FunctionalInterface
public interface CustomerAiKnowledgeJobClient {
    CustomerAiKnowledgeJobAccepted submit(CustomerAiKnowledgeJobCommand command);
}