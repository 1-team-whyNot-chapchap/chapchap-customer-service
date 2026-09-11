package com.chapchap.customer.domain.customerai.service.observability;

import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;

public enum NoOpCustomerAiDiagnosticSink implements CustomerAiDiagnosticSink {
    INSTANCE;

    @Override
    public void emit(CustomerAiDiagnosticEvent event) {
    }
}
