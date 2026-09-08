package com.volatility.modules.task.entity;

import com.volatility.common.util.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "task_main")
public class TaskMainEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String taskNo;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String taskType;

    @Column(nullable = false, length = 32)
    private String taskStatus;

    @Column(nullable = false, length = 16)
    private String stockMode;

    @Column(nullable = false)
    private Integer stockCount;

    @Column(nullable = false)
    private Integer progress;

    @Column(length = 16)
    private String stockCode;

    @Column(length = 64)
    private String stockName;

    @Column(length = 500)
    private String resultSummary;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Column(length = 500)
    private String errorMsg;
}
