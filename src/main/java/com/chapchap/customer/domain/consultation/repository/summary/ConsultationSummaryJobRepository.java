package com.chapchap.customer.domain.consultation.repository.summary;

import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummaryJob;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConsultationSummaryJobRepository extends JpaRepository<ConsultationSummaryJob, Long> {
    @Query("select job.id from ConsultationSummaryJob job where job.cutoffSequenceNo is not null "
            + "and (job.status = com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryJobStatus.PENDING "
            + "or (job.updatedAt <= :before and (job.status in "
            + "(com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryJobStatus.SUBMITTED, "
            + "com.chapchap.customer.domain.consultation.constant.summary.ConsultationSummaryJobStatus.ACCEPTED) "
            + "or (job.attemptCount < 3 and job.retryable = true)))) order by job.updatedAt, job.id")
    java.util.List<Long> findRecoveryCandidates(@Param("before") java.time.LocalDateTime before,
                                              org.springframework.data.domain.Pageable pageable);
    Optional<ConsultationSummaryJob> findByConsultationId(Long consultationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ConsultationSummaryJob job where job.id = :jobId")
    Optional<ConsultationSummaryJob> findByIdForUpdate(@Param("jobId") Long jobId);
}
