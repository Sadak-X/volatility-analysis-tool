package com.volatility.modules.forecast.repository;

import com.volatility.modules.forecast.entity.VolatilityForecastDetailEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface VolatilityForecastDetailRepository extends JpaRepository<VolatilityForecastDetailEntity, Long> {

    List<VolatilityForecastDetailEntity> findByForecastResultIdOrderByRankNoAsc(Long forecastResultId);

    @Transactional
    @Modifying
    @Query("delete from VolatilityForecastDetailEntity d where d.forecastResultId = :forecastResultId")
    void deleteByForecastResultId(@Param("forecastResultId") Long forecastResultId);

    @Transactional
    @Modifying
    @Query("""
            delete from VolatilityForecastDetailEntity d
            where d.forecastResultId in (
                select r.id from VolatilityForecastResultEntity r where r.taskId = :taskId
            )
            """)
    void deleteByTaskId(@Param("taskId") Long taskId);
}
