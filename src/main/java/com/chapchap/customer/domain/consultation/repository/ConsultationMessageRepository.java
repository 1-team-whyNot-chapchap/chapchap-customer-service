package com.chapchap.customer.domain.consultation.repository;

import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationMessageRepository extends JpaRepository<ConsultationMessage, Long> {
    List<ConsultationMessage> findByConsultation_IdOrderBySequenceNoAsc(Long consultationId);
}
