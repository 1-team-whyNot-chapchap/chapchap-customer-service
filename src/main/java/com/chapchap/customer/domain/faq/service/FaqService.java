package com.chapchap.customer.domain.faq.service;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.faq.entity.Faq;
import com.chapchap.customer.domain.faq.repository.FaqRepository;
import com.chapchap.customer.domain.faq.request.FaqCreateRequest;
import com.chapchap.customer.domain.faq.request.FaqUpdateRequest;
import com.chapchap.customer.domain.faq.response.FaqResponse;
import com.chapchap.customer.global.error.custom.faq.FaqNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqService {
    private final FaqRepository faqRepository;
    private final AuditLogWriter auditLogWriter;

    @Transactional(readOnly = true)
    public List<FaqResponse> findPublicFaqs(String category, String keyword) {
        return faqRepository.findPublishedAndActive(normalize(category), normalize(keyword))
                .stream()
                .map(FaqResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FaqResponse findPublicFaq(Long faqId) {
        return FaqResponse.from(findPublicFaqEntity(faqId));
    }

    @Transactional
    public FaqResponse createFaq(Long actorUserId, FaqCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Faq faq = faqRepository.save(Faq.create(
                request.category(),
                request.question(),
                request.answer(),
                request.displayOrder(),
                request.published(),
                actorUserId,
                now
        ));
        auditLogWriter.recordFaqCreated(actorUserId, faq, now);
        return FaqResponse.from(faq);
    }

    @Transactional
    public FaqResponse updateFaq(Long actorUserId, Long faqId, FaqUpdateRequest request) {
        Faq faq = findFaq(faqId);
        Faq before = faq.snapshot();
        LocalDateTime now = LocalDateTime.now();
        faq.update(
                request.category(),
                request.question(),
                request.answer(),
                request.displayOrder(),
                request.published(),
                actorUserId,
                now
        );
        auditLogWriter.recordFaqUpdated(actorUserId, before, faq, now);
        return FaqResponse.from(faq);
    }

    @Transactional
    public FaqResponse deactivateFaq(Long actorUserId, Long faqId) {
        Faq faq = findFaq(faqId);
        Faq before = faq.snapshot();
        LocalDateTime now = LocalDateTime.now();
        if (faq.deactivate(actorUserId, now)) {
            auditLogWriter.recordFaqDeactivated(actorUserId, before, faq, now);
        }
        return FaqResponse.from(faq);
    }

    private Faq findPublicFaqEntity(Long faqId) {
        return faqRepository.findByIdAndPublishedTrueAndActiveTrue(faqId)
                .orElseThrow(this::faqNotFound);
    }

    private Faq findFaq(Long faqId) {
        return faqRepository.findById(faqId)
                .orElseThrow(this::faqNotFound);
    }

    private FaqNotFoundException faqNotFound() {
        return new FaqNotFoundException();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
