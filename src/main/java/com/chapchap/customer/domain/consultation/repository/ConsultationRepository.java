package com.chapchap.customer.domain.consultation.repository;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    Optional<Consultation> findByIdAndUserId(Long id, Long userId);
}
