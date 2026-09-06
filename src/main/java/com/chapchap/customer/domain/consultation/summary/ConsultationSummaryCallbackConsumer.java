package com.chapchap.customer.domain.consultation.summary;

import java.util.Objects;

public final class ConsultationSummaryCallbackConsumer {
    private final ConsultationSummaryCallbackParser parser;
    private final ConsultationSummaryCallbackStatePort statePort;

    public ConsultationSummaryCallbackConsumer(
            ConsultationSummaryCallbackParser parser,
            ConsultationSummaryCallbackStatePort statePort
    ) {
        this.parser = Objects.requireNonNull(parser);
        this.statePort = Objects.requireNonNull(statePort);
    }

    public ConsultationSummaryCallbackOutcome consumeVerified(
            ConsultationSummaryCallbackHeaders headers,
            String body
    ) {
        ConsultationSummaryCallback callback = parser.parse(body, headers);
        ConsultationSummaryCallbackOutcome outcome = statePort.applyAtomically(headers, callback);
        if (outcome == null || outcome == ConsultationSummaryCallbackOutcome.CONFLICT) {
            throw new ConsultationSummaryCallbackException(
                    ConsultationSummaryCallbackException.Reason.STATE_CONFLICT);
        }
        return outcome;
    }
}