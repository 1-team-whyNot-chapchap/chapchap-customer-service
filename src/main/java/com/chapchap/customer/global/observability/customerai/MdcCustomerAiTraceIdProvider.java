package com.chapchap.customer.global.observability.customerai;

import org.slf4j.MDC;

import java.util.regex.Pattern;

public final class MdcCustomerAiTraceIdProvider implements CustomerAiTraceIdProvider {
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    public String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null && TRACE_ID_PATTERN.matcher(traceId).matches() ? traceId : null;
    }
}
