package com.volatility.modules.forecast.repository;

import com.volatility.modules.forecast.entity.VolatilityForecastResultEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface VolatilityForecastResultRepository extends JpaRepository<VolatilityForecastResultEntity, Long> {

    Page<VolatilityForecastResultEntity> findByStockCodeContaining(String stockCode, Pageable pageable);

    long countByStockCodeContainingAndRiskLevel(String stockCode, String riskLevel);

    List<VolatilityForecastResultEntity> findByTaskId(Long taskId);

    List<VolatilityForecastResultEntity> findByTaskIdOrderByIdAsc(Long taskId);

    Optional<VolatilityForecastResultEntity> findFirstByTaskIdOrderByCreateTimeDesc(Long taskId);

    @Transactional
    void deleteByTaskId(Long taskId);
}
