package com.chapchap.customer.domain.consultation.repository;

import com.chapchap.customer.domain.consultation.entity.ConsultationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultationMessageRepository extends JpaRepository<ConsultationMessage, Long> {
    List<ConsultationMessage> findByConsultation_IdOrderBySequenceNoAsc(Long consultationId);

    Optional<ConsultationMessage> findTopByConsultation_IdOrderBySequenceNoDesc(Long consultationId);
}
