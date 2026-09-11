package com.chapchap.customer.domain.consultation.repository.summary;

import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummary;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConsultationSummaryRepository extends JpaRepository<ConsultationSummary, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select summary from ConsultationSummary summary where summary.consultationId = :consultationId")
    Optional<ConsultationSummary> findByConsultationIdForUpdate(@Param("consultationId") Long consultationId);
}
