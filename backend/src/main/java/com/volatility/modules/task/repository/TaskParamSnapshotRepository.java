package com.volatility.modules.task.repository;

import com.volatility.modules.task.entity.TaskParamSnapshotEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TaskParamSnapshotRepository extends JpaRepository<TaskParamSnapshotEntity, Long> {

    Optional<TaskParamSnapshotEntity> findByTaskId(Long taskId);

    List<TaskParamSnapshotEntity> findByDataSourceTypeIgnoreCase(String dataSourceType);
    @Transactional
    void deleteByTaskId(Long taskId);
}
