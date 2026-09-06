package com.chapchap.customer.domain.knowledge.processing.async;

@FunctionalInterface
public interface CustomerAiKnowledgeJobClient {
    CustomerAiKnowledgeJobAccepted submit(CustomerAiKnowledgeJobCommand command);
}