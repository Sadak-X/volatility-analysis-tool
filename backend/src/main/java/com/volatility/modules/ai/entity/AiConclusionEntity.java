package com.volatility.modules.ai.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_conclusion")
public class AiConclusionEntity extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String module;

    @Column(nullable = false, unique = true, length = 64)
    private String inputHash;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String insightJson;
}