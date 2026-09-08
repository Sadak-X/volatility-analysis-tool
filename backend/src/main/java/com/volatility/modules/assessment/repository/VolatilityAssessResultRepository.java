package com.volatility.modules.assessment.repository;

import com.volatility.modules.assessment.entity.VolatilityAssessResultEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface VolatilityAssessResultRepository extends JpaRepository<VolatilityAssessResultEntity, Long> {

    Optional<VolatilityAssessResultEntity> findFirstByTaskIdOrderByCreateTimeDesc(Long taskId);

    Optional<VolatilityAssessResultEntity> findFirstByTaskIdAndStockCodeOrderByCreateTimeDesc(Long taskId, String stockCode);

    @Transactional
    void deleteByTaskId(Long taskId);
}
