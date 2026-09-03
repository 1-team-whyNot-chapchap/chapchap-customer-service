package com.chapchap.customer.domain.quality.service;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.quality.entity.QualityInquiry;
import com.chapchap.customer.domain.quality.entity.QualityInquiryStatus;
import com.chapchap.customer.domain.quality.entity.QualityInquiryType;
import com.chapchap.customer.domain.quality.file.QualityInquiryFileValidator;
import com.chapchap.customer.domain.quality.file.ValidatedQualityInquiryFile;
import com.chapchap.customer.domain.quality.repository.QualityInquiryRepository;
import com.chapchap.customer.domain.quality.request.QualityInquiryCreateRequest;
import com.chapchap.customer.domain.quality.request.QualityInquiryProcessRequest;
import com.chapchap.customer.domain.quality.storage.QualityInquiryObjectStorage;
import com.chapchap.customer.global.error.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityInquiryServiceTest {
    @Mock
    private QualityInquiryRepository qualityInquiryRepository;

    @Mock
    private QualityInquiryPersistenceService persistenceService;

    @Mock
    private QualityInquiryFileValidator fileValidator;

    @Mock
    private QualityInquiryObjectStorage objectStorage;

    @Mock
    private AuditLogWriter auditLogWriter;

    @InjectMocks
    private QualityInquiryService qualityInquiryService;

    @Test
    void createsReceivedInquiryWithRequiredDamageReferences() {
        QualityInquiry inquiry = inquiry(3L, 7L);
        when(persistenceService.persist(any(QualityInquiry.class))).thenReturn(inquiry);

        var response = qualityInquiryService.create(7L, createRequest(QualityInquiryType.DAMAGED));

        ArgumentCaptor<QualityInquiry> captor = ArgumentCaptor.forClass(QualityInquiry.class);
        verify(persistenceService).persist(captor.capture());
        assertThat(response.qualityInquiryId()).isEqualTo(3L);
        assertThat(captor.getValue().getStatus()).isEqualTo(QualityInquiryStatus.RECEIVED);
        assertThat(captor.getValue().getOrderId()).isEqualTo(10L);
        assertThat(captor.getValue().getProductId()).isEqualTo(20L);
    }

    @Test
    void rejectsDamageInquiryWithoutProductReferenceBeforeUploadingFile() {
        QualityInquiryCreateRequest request = createRequest(QualityInquiryType.DAMAGED);
        request.setProductId(null);

        assertThatThrownBy(() -> qualityInquiryService.create(7L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void removesStoredObjectWhenDatabasePersistenceFails() {
        QualityInquiryCreateRequest request = createRequest(QualityInquiryType.DAMAGED);
        MockMultipartFile file = new MockMultipartFile("attachments", "damage.png", "image/png", new byte[]{1});
        request.setAttachments(List.of(file));
        when(fileValidator.validate(file)).thenReturn(new ValidatedQualityInquiryFile("damage.png", "image/png", 1, new byte[]{1}));
        when(persistenceService.persist(any(QualityInquiry.class))).thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> qualityInquiryService.create(7L, request))
                .isInstanceOf(IllegalStateException.class);

        verify(objectStorage).store(any(), any(), eq(1L), eq("image/png"));
        verify(objectStorage).delete(any());
    }

    @Test
    void hidesOtherUsersInquiryAsNotFound() {
        when(qualityInquiryRepository.findByIdAndUserId(3L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qualityInquiryService.findMyInquiry(8L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCustomResponseCode().getCode()).isEqualTo("E10"));
    }

    @Test
    void processesOnlyNextStatusAndWritesAuditLog() {
        QualityInquiry inquiry = inquiry(3L, 7L);
        when(qualityInquiryRepository.findById(3L)).thenReturn(Optional.of(inquiry));

        var response = qualityInquiryService.process(
                11L,
                3L,
                new QualityInquiryProcessRequest(QualityInquiryType.DAMAGED, "HIGH", QualityInquiryStatus.IN_PROGRESS, null)
        );

        assertThat(response.status()).isEqualTo(QualityInquiryStatus.IN_PROGRESS);
        assertThat(response.assignedAdminId()).isEqualTo(11L);
        verify(auditLogWriter).recordQualityInquiryProcessed(eq(11L), any(QualityInquiry.class), eq(inquiry), any());
    }

    @Test
    void rejectsSkippedStatusTransition() {
        QualityInquiry inquiry = inquiry(3L, 7L);
        when(qualityInquiryRepository.findById(3L)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> qualityInquiryService.process(
                11L,
                3L,
                new QualityInquiryProcessRequest(QualityInquiryType.DAMAGED, "HIGH", QualityInquiryStatus.RESOLVED, "처리했습니다.")
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void keepsResolvedAnswerWhenClosingInquiryWithoutAnswerChange() {
        QualityInquiry inquiry = inquiry(3L, 7L);
        LocalDateTime now = LocalDateTime.now();
        inquiry.process(11L, QualityInquiryType.DAMAGED, "HIGH", QualityInquiryStatus.IN_PROGRESS, null, now);
        inquiry.process(11L, QualityInquiryType.DAMAGED, "HIGH", QualityInquiryStatus.RESOLVED, "처리했습니다.", now.plusMinutes(1));
        when(qualityInquiryRepository.findById(3L)).thenReturn(Optional.of(inquiry));

        var response = qualityInquiryService.process(
                11L,
                3L,
                new QualityInquiryProcessRequest(QualityInquiryType.DAMAGED, "HIGH", QualityInquiryStatus.CLOSED, null)
        );

        assertThat(response.status()).isEqualTo(QualityInquiryStatus.CLOSED);
        assertThat(response.adminAnswer()).isEqualTo("처리했습니다.");
        assertThat(response.closedAt()).isNotNull();
    }

    private QualityInquiryCreateRequest createRequest(QualityInquiryType inquiryType) {
        QualityInquiryCreateRequest request = new QualityInquiryCreateRequest();
        request.setInquiryType(inquiryType);
        request.setContent("  파손 문의  ");
        request.setOrderId(10L);
        request.setProductId(20L);
        return request;
    }

    private QualityInquiry inquiry(Long inquiryId, Long userId) {
        QualityInquiry inquiry = QualityInquiry.create(
                userId, 10L, 20L, null, QualityInquiryType.DAMAGED, "파손 문의", LocalDateTime.now()
        );
        ReflectionTestUtils.setField(inquiry, "id", inquiryId);
        return inquiry;
    }
}
