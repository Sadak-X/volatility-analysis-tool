package com.volatility.modules.ai.repository;

import com.volatility.modules.ai.entity.AiAnalysisReportEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisReportRepository extends JpaRepository<AiAnalysisReportEntity, Long> {

    Optional<AiAnalysisReportEntity> findFirstByTaskIdOrderByCreateTimeDesc(Long taskId);

    Optional<AiAnalysisReportEntity> findFirstByTaskIdAndPromptHashOrderByCreateTimeDesc(Long taskId, String promptHash);
}
