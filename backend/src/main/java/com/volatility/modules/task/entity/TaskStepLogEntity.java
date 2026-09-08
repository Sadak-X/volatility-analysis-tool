package com.volatility.modules.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "task_step_log")
public class TaskStepLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 32)
    private String stepCode;

    @Column(nullable = false, length = 32)
    private String stepStatus;

    @Column(length = 500)
    private String messageText;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String detailJson;

    @Column(nullable = false)
    private LocalDateTime createTime;
}
