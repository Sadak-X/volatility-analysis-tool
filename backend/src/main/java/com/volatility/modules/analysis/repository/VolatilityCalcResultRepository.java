package com.volatility.modules.analysis.repository;

import com.volatility.modules.analysis.entity.VolatilityCalcResultEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface VolatilityCalcResultRepository extends JpaRepository<VolatilityCalcResultEntity, Long> {

    List<VolatilityCalcResultEntity> findByTaskId(Long taskId);

    Optional<VolatilityCalcResultEntity> findFirstByTaskIdOrderByCreateTimeDesc(Long taskId);

    Optional<VolatilityCalcResultEntity> findFirstByTaskIdAndStockCodeOrderByCreateTimeDesc(Long taskId, String stockCode);

    @Transactional
    void deleteByTaskId(Long taskId);
}
