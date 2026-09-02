package com.chapchap.customer.domain.consultation.repository;

import com.chapchap.customer.domain.consultation.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {
    Optional<Consultation> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select consultation from Consultation consultation where consultation.id = :consultationId")
    Optional<Consultation> findByIdForMessageWrite(@Param("consultationId") Long consultationId);

    List<Consultation> findByStatusOrderByCreatedAtAsc(com.chapchap.customer.domain.consultation.entity.ConsultationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Consultation consultation
               set consultation.status = com.chapchap.customer.domain.consultation.entity.ConsultationStatus.IN_PROGRESS,
                   consultation.assignedAdminId = :adminId,
                   consultation.assignedAt = :now,
                   consultation.updatedAt = :now
             where consultation.id = :consultationId
               and consultation.status = com.chapchap.customer.domain.consultation.entity.ConsultationStatus.WAITING_ADMIN
               and consultation.assignedAdminId is null
            """)
    int acceptWaitingConsultation(@Param("consultationId") Long consultationId, @Param("adminId") Long adminId,
                                  @Param("now") LocalDateTime now);
}
