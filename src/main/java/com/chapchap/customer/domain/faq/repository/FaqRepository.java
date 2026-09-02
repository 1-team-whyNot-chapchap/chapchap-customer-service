package com.chapchap.customer.domain.faq.repository;

import com.chapchap.customer.domain.faq.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    @Query("""
            select faq
            from Faq faq
            where faq.published = true
              and faq.active = true
              and (:category is null or faq.category = :category)
              and (:keyword is null
                   or lower(faq.question) like lower(concat('%', :keyword, '%'))
                   or lower(faq.answer) like lower(concat('%', :keyword, '%')))
            order by faq.displayOrder asc, faq.id asc
            """)
    List<Faq> findPublishedAndActive(
            @Param("category") String category,
            @Param("keyword") String keyword
    );

    Optional<Faq> findByIdAndPublishedTrueAndActiveTrue(Long id);
}
