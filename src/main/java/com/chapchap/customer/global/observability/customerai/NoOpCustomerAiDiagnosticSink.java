package com.chapchap.customer.global.observability.customerai;

public enum NoOpCustomerAiDiagnosticSink implements CustomerAiDiagnosticSink {
    INSTANCE;

    @Override
    public void emit(CustomerAiDiagnosticEvent event) {
    }
}
