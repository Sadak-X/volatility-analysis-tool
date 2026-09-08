package com.volatility.modules.task.repository;

import com.volatility.modules.task.entity.TaskStepLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TaskStepLogRepository extends JpaRepository<TaskStepLogEntity, Long> {

    List<TaskStepLogEntity> findByTaskIdOrderByCreateTimeAsc(Long taskId);
    @Transactional
    void deleteByTaskId(Long taskId);
}
