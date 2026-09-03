package com.chapchap.customer.domain.quality.repository;

import com.chapchap.customer.domain.quality.entity.QualityInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QualityInquiryRepository extends JpaRepository<QualityInquiry, Long> {
    List<QualityInquiry> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<QualityInquiry> findByIdAndUserId(Long id, Long userId);

    List<QualityInquiry> findAllByOrderByCreatedAtDesc();
}
