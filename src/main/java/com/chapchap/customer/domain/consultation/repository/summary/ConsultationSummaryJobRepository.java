package com.chapchap.customer.domain.consultation.repository.summary;

import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummaryJob;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConsultationSummaryJobRepository extends JpaRepository<ConsultationSummaryJob, Long> {
    Optional<ConsultationSummaryJob> findByConsultationId(Long consultationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ConsultationSummaryJob job where job.id = :jobId")
    Optional<ConsultationSummaryJob> findByIdForUpdate(@Param("jobId") Long jobId);
}
