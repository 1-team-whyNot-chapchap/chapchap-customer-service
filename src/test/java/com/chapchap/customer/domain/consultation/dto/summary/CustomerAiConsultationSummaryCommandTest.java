package com.chapchap.customer.domain.consultation.dto.summary;

import com.chapchap.customer.domain.consultation.constant.ConsultationStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAiConsultationSummaryCommandTest {
    @Test
    void createsDeterministicConsultationIdempotencyKey() {
        assertThat(command(ConsultationStatus.CLOSED, messages()).idempotencyKey())
                .isEqualTo("consultation-summary:501");
    }

    @Test
    void requiresConsultationToBeClosedBeforeSubmission() {
        assertThatThrownBy(() -> command(ConsultationStatus.IN_PROGRESS, messages()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void acceptsUserAdminAndAiMessagesAndRejectsInvalidContent() {
        assertThat(command(ConsultationStatus.CLOSED, List.of(
                new CustomerAiConsultationSummaryCommand.Message(
                        CustomerAiConsultationSummaryCommand.SenderType.USER, "문의"),
                new CustomerAiConsultationSummaryCommand.Message(
                        CustomerAiConsultationSummaryCommand.SenderType.ADMIN, "답변"),
                new CustomerAiConsultationSummaryCommand.Message(
                        CustomerAiConsultationSummaryCommand.SenderType.AI, "AI 답변")
        )).messages()).hasSize(3);

        assertThatThrownBy(() -> new CustomerAiConsultationSummaryCommand.Message(
                CustomerAiConsultationSummaryCommand.SenderType.USER, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capsSubmittedMessageSnapshot() {
        ArrayList<CustomerAiConsultationSummaryCommand.Message> tooMany = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            tooMany.add(new CustomerAiConsultationSummaryCommand.Message(
                    CustomerAiConsultationSummaryCommand.SenderType.USER, "message-" + index));
        }

        assertThatThrownBy(() -> command(ConsultationStatus.CLOSED, tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages size");
    }

    private CustomerAiConsultationSummaryCommand command(
            ConsultationStatus status,
            List<CustomerAiConsultationSummaryCommand.Message> messages
    ) {
        return new CustomerAiConsultationSummaryCommand(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                7001L,
                501L,
                status,
                messages
        );
    }

    private List<CustomerAiConsultationSummaryCommand.Message> messages() {
        return List.of(new CustomerAiConsultationSummaryCommand.Message(
                CustomerAiConsultationSummaryCommand.SenderType.USER, "환불 문의"));
    }
}