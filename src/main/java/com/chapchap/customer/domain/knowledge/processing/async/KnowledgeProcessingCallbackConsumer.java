package com.chapchap.customer.domain.knowledge.processing.async;

import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticEvent;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticFailureCode;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticOutcome;
import com.chapchap.customer.global.observability.customerai.CustomerAiDiagnosticPublisher;

import java.util.Objects;

public final class KnowledgeProcessingCallbackConsumer {
    private final KnowledgeProcessingCallbackParser parser;
    private final KnowledgeProcessingCallbackStatePort statePort;
    private final CustomerAiDiagnosticPublisher diagnostics;

    public KnowledgeProcessingCallbackConsumer(
            KnowledgeProcessingCallbackParser parser,
            KnowledgeProcessingCallbackStatePort statePort
    ) {
        this(parser, statePort, CustomerAiDiagnosticPublisher.noOp());
    }

    public KnowledgeProcessingCallbackConsumer(
            KnowledgeProcessingCallbackParser parser,
            KnowledgeProcessingCallbackStatePort statePort,
            CustomerAiDiagnosticPublisher diagnostics
    ) {
        this.parser = Objects.requireNonNull(parser);
        this.statePort = Objects.requireNonNull(statePort);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    public KnowledgeProcessingCallbackOutcome consumeVerified(
            KnowledgeProcessingCallbackHeaders headers,
            String body
    ) {
        try {
            KnowledgeProcessingCallback callback = parser.parse(body, headers);
            KnowledgeProcessingCallbackOutcome outcome = statePort.applyAtomically(headers, callback);
            if (outcome == null || outcome == KnowledgeProcessingCallbackOutcome.CONFLICT) {
                throw new KnowledgeProcessingCallbackException(
                        KnowledgeProcessingCallbackException.Reason.STATE_CONFLICT);
            }
            emitResult(headers, callback, outcome);
            return outcome;
        } catch (KnowledgeProcessingCallbackException exception) {
            if (headers != null) {
                diagnostics.publish(traceId -> CustomerAiDiagnosticEvent.knowledgeCallbackRejected(
                        headers.requestId(),
                        traceId,
                        headers.idempotencyProcessingId(),
                        CustomerAiDiagnosticFailureCode.from(exception.reason())
                ));
            }
            throw exception;
        }
    }

    private void emitResult(
            KnowledgeProcessingCallbackHeaders headers,
            KnowledgeProcessingCallback callback,
            KnowledgeProcessingCallbackOutcome outcome
    ) {
        switch (outcome) {
            case APPLIED_COMPLETED -> diagnostics.publish(traceId ->
                    CustomerAiDiagnosticEvent.knowledgeCallbackCompleted(
                            headers.requestId(),
                            traceId,
                            callback.knowledgeVersionId(),
                            callback.processingId(),
                            callback.chunkCount()
                    ));
            case APPLIED_FAILED -> diagnostics.publish(traceId ->
                    CustomerAiDiagnosticEvent.knowledgeCallbackFailed(
                            headers.requestId(),
                            traceId,
                            callback.knowledgeVersionId(),
                            callback.processingId(),
                            CustomerAiDiagnosticFailureCode.from(callback.failureCode()),
                            callback.retryable()
                    ));
            case IGNORED_DUPLICATE, IGNORED_STALE -> diagnostics.publish(traceId ->
                    CustomerAiDiagnosticEvent.knowledgeCallbackIgnored(
                            headers.requestId(),
                            traceId,
                            callback.knowledgeVersionId(),
                            callback.processingId(),
                            CustomerAiDiagnosticOutcome.valueOf(outcome.name())
                    ));
            case CONFLICT -> throw new IllegalStateException("Conflict must be rejected before diagnostics.");
        }
    }
}
