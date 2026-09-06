package com.chapchap.customer.global.observability.customerai;

@FunctionalInterface
public interface CustomerAiDiagnosticSink {
    void emit(CustomerAiDiagnosticEvent event);
}
