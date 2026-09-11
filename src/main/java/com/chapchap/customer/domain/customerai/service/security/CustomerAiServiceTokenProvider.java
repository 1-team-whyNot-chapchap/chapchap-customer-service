package com.chapchap.customer.domain.customerai.service.security;

@FunctionalInterface
public interface CustomerAiServiceTokenProvider {
    String getServiceToken();
}
