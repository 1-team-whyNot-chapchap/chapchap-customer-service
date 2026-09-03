package com.chapchap.customer.domain.faq.service;

import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.domain.faq.entity.Faq;
import com.chapchap.customer.domain.faq.repository.FaqRepository;
import com.chapchap.customer.domain.faq.request.FaqCreateRequest;
import com.chapchap.customer.domain.faq.request.FaqUpdateRequest;
import com.chapchap.customer.global.error.custom.faq.FaqNotFoundException;
import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {
    @Mock
    private FaqRepository faqRepository;

    @Mock
    private AuditLogWriter auditLogWriter;

    @InjectMocks
    private FaqService faqService;

    private Faq faq;

    @BeforeEach
    void setUp() {
        faq = Faq.create("DELIVERY", "배송은 언제 오나요?", "배송 일정에 따라 도착합니다.", 1, true, 1L, LocalDateTime.now());
    }

    @Test
    void findsPublicFaqsUsingOnlyNormalizedSearchConditions() {
        when(faqRepository.findPublishedAndActive("DELIVERY", "배송"))
                .thenReturn(List.of(faq));

        var responses = faqService.findPublicFaqs(" DELIVERY ", " 배송 ");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().question()).isEqualTo("배송은 언제 오나요?");
    }

    @Test
    void hidesFaqThatIsNotPublicAndActive() {
        when(faqRepository.findByIdAndPublishedTrueAndActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.findPublicFaq(1L))
                .isInstanceOf(FaqNotFoundException.class)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCustomResponseCode())
                                .isEqualTo(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR));
    }

    @Test
    void recordsAuditLogWhenFaqIsCreated() {
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FaqCreateRequest request = new FaqCreateRequest("PAYMENT", "결제일은 언제인가요?", "매월 지정일에 결제됩니다.", 0, true);

        faqService.createFaq(10L, request);

        verify(auditLogWriter).recordFaqCreated(eq(10L), any(Faq.class), any(LocalDateTime.class));
    }

    @Test
    void recordsOnlyOneDeactivationAuditForRepeatedRequest() {
        when(faqRepository.findById(1L)).thenReturn(Optional.of(faq));

        faqService.deactivateFaq(10L, 1L);
        faqService.deactivateFaq(10L, 1L);

        assertThat(faq.isActive()).isFalse();
        verify(auditLogWriter, times(1)).recordFaqDeactivated(eq(10L), any(Faq.class), eq(faq), any(LocalDateTime.class));
    }

    @Test
    void recordsBeforeAndAfterStateWhenFaqIsUpdated() {
        when(faqRepository.findById(1L)).thenReturn(Optional.of(faq));
        FaqUpdateRequest request = new FaqUpdateRequest("PAYMENT", "결제일을 알려주세요.", "매월 지정일에 결제됩니다.", 3, false);

        faqService.updateFaq(10L, 1L, request);

        assertThat(faq.getCategory()).isEqualTo("PAYMENT");
        assertThat(faq.isPublished()).isFalse();
        verify(auditLogWriter).recordFaqUpdated(eq(10L), any(Faq.class), eq(faq), any(LocalDateTime.class));
    }
}
