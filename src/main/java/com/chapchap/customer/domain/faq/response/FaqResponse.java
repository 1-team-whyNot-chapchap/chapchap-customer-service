package com.chapchap.customer.domain.faq.response;

import com.chapchap.customer.domain.faq.entity.Faq;

public record FaqResponse(
        Long faqId,
        String category,
        String question,
        String answer,
        int displayOrder,
        boolean published
) {
    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getCategory(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getDisplayOrder(),
                faq.isPublished()
        );
    }
}
