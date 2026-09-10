package com.chapchap.customer.domain.customerai.service.observability;

import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;

@FunctionalInterface
public interface CustomerAiDiagnosticSink {
    void emit(CustomerAiDiagnosticEvent event);
}
