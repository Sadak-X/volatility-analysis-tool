package com.volatility.modules.ai.repository;

import com.volatility.modules.ai.entity.AiConclusionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConclusionRepository extends JpaRepository<AiConclusionEntity, Long> {
    Optional<AiConclusionEntity> findByModuleAndInputHash(String module, String inputHash);
}