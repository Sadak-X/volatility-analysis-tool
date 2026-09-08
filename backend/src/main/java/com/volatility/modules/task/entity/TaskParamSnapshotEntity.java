package com.volatility.modules.task.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "task_param_snapshot")
public class TaskParamSnapshotEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long taskId;

    @Column(nullable = false, length = 16)
    private String dataSourceType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String stockCodesJson;

    @Column(nullable = false)
    private LocalDate dateStart;

    @Column(nullable = false)
    private LocalDate dateEnd;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal confidenceLevel;

    @Column(nullable = false, length = 8)
    private String timeGranularity;

    @Column(nullable = false, length = 8)
    private String forecastHorizon;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String paramJson;
}
