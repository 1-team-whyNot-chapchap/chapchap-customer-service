package com.chapchap.customer.domain.csreadmodel.service;

import com.chapchap.customer.domain.csreadmodel.entity.CsReadModel;
import com.chapchap.customer.domain.csreadmodel.entity.CsReadModelProjectionType;
import com.chapchap.customer.domain.csreadmodel.repository.CsReadModelRepository;
import com.chapchap.customer.global.kafka.event.CustomerKafkaEvent;
import com.chapchap.customer.global.kafka.service.CustomerKafkaEventValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CsReadModelProjectionServiceTest {
    private final CsReadModelRepository csReadModelRepository = mock(CsReadModelRepository.class);
    private final CsReadModelProjectionService projectionService = new CsReadModelProjectionService(
            csReadModelRepository,
            new CustomerKafkaEventValidator()
    );

    @Test
    void createsPaymentProjectionFromHigherVersionEvent() {
        CustomerKafkaEvent event = event("PAYMENT_COMPLETED", 7L, Map.of(
                "paymentId", "3d594650-3436-4c0d-9fba-f4ec7fdb66e4",
                "paymentVersion", 1
        ));
        when(csReadModelRepository.findByProjectionTypeAndAggregateId(
                CsReadModelProjectionType.PAYMENT, "3d594650-3436-4c0d-9fba-f4ec7fdb66e4"
        )).thenReturn(Optional.empty());
        ArgumentCaptor<CsReadModel> captor = ArgumentCaptor.forClass(CsReadModel.class);

        projectionService.project("3d594650-3436-4c0d-9fba-f4ec7fdb66e4", event);

        verify(csReadModelRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getBusinessVersion()).isEqualTo(1L);
    }

    @Test
    void ignoresSameOrLowerBusinessVersion() {
        CsReadModel existing = CsReadModel.create(
                7L, CsReadModelProjectionType.PAYMENT, "3d594650-3436-4c0d-9fba-f4ec7fdb66e4",
                "COMPLETED", false, 2L, "0198a8e8-2acd-7b24-a682-b50c6784515a",
                LocalDateTime.now(), LocalDateTime.now()
        );
        CustomerKafkaEvent event = event("PAYMENT_COMPLETED", 7L, Map.of(
                "paymentId", "3d594650-3436-4c0d-9fba-f4ec7fdb66e4",
                "paymentVersion", 2
        ));
        when(csReadModelRepository.findByProjectionTypeAndAggregateId(
                CsReadModelProjectionType.PAYMENT, "3d594650-3436-4c0d-9fba-f4ec7fdb66e4"
        )).thenReturn(Optional.of(existing));

        projectionService.project("3d594650-3436-4c0d-9fba-f4ec7fdb66e4", event);

        verify(csReadModelRepository, never()).save(any());
        assertThat(existing.getStatus()).isEqualTo("COMPLETED");
        assertThat(existing.getBusinessVersion()).isEqualTo(2L);
    }

    @Test
    void doesNotCreateDeliveryProjectionFromDelayedFactOnly() {
        CustomerKafkaEvent event = event("DELIVERY_DELAYED", 7L, Map.of(
                "deliveryId", "e5cab8e3-82fb-4918-a52e-9f01136931bd"
        ));
        when(csReadModelRepository.findByProjectionTypeAndAggregateId(
                CsReadModelProjectionType.DELIVERY, "e5cab8e3-82fb-4918-a52e-9f01136931bd"
        )).thenReturn(Optional.empty());

        projectionService.project("e5cab8e3-82fb-4918-a52e-9f01136931bd", event);

        verify(csReadModelRepository, never()).save(any());
    }

    private CustomerKafkaEvent event(String eventType, Long userId, Map<String, Object> data) {
        return new CustomerKafkaEvent(
                "0198a8e8-2acd-7b24-a682-b50c6784515a",
                eventType,
                1,
                "2026-09-02T15:00:00+09:00",
                userId,
                data
        );
    }
}
