package com.chapchap.customer.domain.quality.service;

import com.chapchap.customer.domain.quality.entity.QualityInquiry;
import com.chapchap.customer.domain.quality.repository.QualityInquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QualityInquiryPersistenceService {
    private final QualityInquiryRepository qualityInquiryRepository;

    @Transactional
    public QualityInquiry persist(QualityInquiry inquiry) {
        return qualityInquiryRepository.save(inquiry);
    }
}
