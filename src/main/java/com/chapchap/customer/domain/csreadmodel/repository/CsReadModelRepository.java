package com.chapchap.customer.domain.csreadmodel.repository;

import com.chapchap.customer.domain.csreadmodel.entity.CsReadModel;
import com.chapchap.customer.domain.csreadmodel.entity.CsReadModelProjectionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CsReadModelRepository extends JpaRepository<CsReadModel, Long> {
    Optional<CsReadModel> findByProjectionTypeAndAggregateId(
            CsReadModelProjectionType projectionType,
            String aggregateId
    );
}
