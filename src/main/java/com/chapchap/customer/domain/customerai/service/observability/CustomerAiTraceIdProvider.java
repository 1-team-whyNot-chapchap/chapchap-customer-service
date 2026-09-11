package com.chapchap.customer.domain.customerai.service.observability;

@FunctionalInterface
public interface CustomerAiTraceIdProvider {
    String currentTraceId();
}
