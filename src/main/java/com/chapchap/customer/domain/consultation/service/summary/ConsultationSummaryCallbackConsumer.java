package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryCallbackOutcome;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallback;
import com.chapchap.customer.domain.consultation.dto.summary.ConsultationSummaryCallbackHeaders;
import com.chapchap.customer.global.exception.consultation.summary.ConsultationSummaryCallbackException;

import com.chapchap.customer.domain.customerai.dto.observability.CustomerAiDiagnosticEvent;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.domain.customerai.constant.observability.CustomerAiDiagnosticOutcome;
import com.chapchap.customer.domain.customerai.service.observability.CustomerAiDiagnosticPublisher;

import java.util.Objects;

public final class ConsultationSummaryCallbackConsumer {
    private final ConsultationSummaryCallbackParser parser;
    private final ConsultationSummaryCallbackStatePort statePort;
    private final CustomerAiDiagnosticPublisher diagnostics;

    public ConsultationSummaryCallbackConsumer(
            ConsultationSummaryCallbackParser parser,
            ConsultationSummaryCallbackStatePort statePort
    ) {
        this(parser, statePort, CustomerAiDiagnosticPublisher.noOp());
    }

    public ConsultationSummaryCallbackConsumer(
            ConsultationSummaryCallbackParser parser,
            ConsultationSummaryCallbackStatePort statePort,
            CustomerAiDiagnosticPublisher diagnostics
    ) {
        this.parser = Objects.requireNonNull(parser);
        this.statePort = Objects.requireNonNull(statePort);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public ConsultationSummaryCallbackOutcome consumeVerified(
            ConsultationSummaryCallbackHeaders headers,
            String body
    ) {
        try {
            ConsultationSummaryCallback callback = parser.parse(body, headers);
            ConsultationSummaryCallbackOutcome outcome = statePort.applyAtomically(headers, callback);
            if (outcome == null || outcome == ConsultationSummaryCallbackOutcome.CONFLICT) {
                throw new ConsultationSummaryCallbackException(
                        ConsultationSummaryCallbackException.Reason.STATE_CONFLICT);
            }
            emitResult(headers, callback, outcome);
            return outcome;
        } catch (ConsultationSummaryCallbackException exception) {
            if (headers != null) {
                diagnostics.publish(traceId -> CustomerAiDiagnosticEvent.summaryCallbackRejected(
                        headers.requestId(),
                        traceId,
                        CustomerAiDiagnosticFailureCode.from(exception.reason())
                ));
            }
            throw exception;
        }
    }

    private void emitResult(
            ConsultationSummaryCallbackHeaders headers,
            ConsultationSummaryCallback callback,
            ConsultationSummaryCallbackOutcome outcome
    ) {
        switch (outcome) {
            case APPLIED_COMPLETED -> diagnostics.publish(traceId ->
                    CustomerAiDiagnosticEvent.summaryCallbackCompleted(
                            headers.requestId(), traceId, callback.consultationId()));
            case APPLIED_FAILED -> diagnostics.publish(traceId ->
                    CustomerAiDiagnosticEvent.summaryCallbackFailed(
                            headers.requestId(),
                            traceId,
                            callback.consultationId(),
                            CustomerAiDiagnosticFailureCode.from(callback.failureCode()),
                            callback.retryable()
                    ));
            case IGNORED_DUPLICATE, IGNORED_STALE -> diagnostics.publish(traceId ->
                    CustomerAiDiagnosticEvent.summaryCallbackIgnored(
                            headers.requestId(),
                            traceId,
                            callback.consultationId(),
                            CustomerAiDiagnosticOutcome.valueOf(outcome.name())
                    ));
            case CONFLICT -> throw new IllegalStateException("Conflict must be rejected before diagnostics.");
        }
    }
}
