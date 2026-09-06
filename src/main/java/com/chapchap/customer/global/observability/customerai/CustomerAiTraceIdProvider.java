package com.chapchap.customer.global.observability.customerai;

@FunctionalInterface
public interface CustomerAiTraceIdProvider {
    String currentTraceId();
}
