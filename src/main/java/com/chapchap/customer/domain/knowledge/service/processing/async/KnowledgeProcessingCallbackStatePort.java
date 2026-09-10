package com.chapchap.customer.domain.knowledge.service.processing.async;

import com.chapchap.customer.domain.knowledge.constant.processing.async.KnowledgeProcessingCallbackOutcome;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallback;
import com.chapchap.customer.domain.knowledge.dto.processing.async.KnowledgeProcessingCallbackHeaders;

public interface KnowledgeProcessingCallbackStatePort {
    KnowledgeProcessingCallbackOutcome applyAtomically(
            KnowledgeProcessingCallbackHeaders headers,
            KnowledgeProcessingCallback callback
    );
}