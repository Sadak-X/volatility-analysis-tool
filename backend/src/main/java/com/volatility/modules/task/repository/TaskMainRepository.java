package com.volatility.modules.task.repository;

import com.volatility.modules.task.entity.TaskMainEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskMainRepository extends JpaRepository<TaskMainEntity, Long> {

    Optional<TaskMainEntity> findByTaskNo(String taskNo);

    Page<TaskMainEntity> findByTaskStatusContainingAndStockCodeContainingAndTaskTypeContaining(
            String taskStatus, String stockCode, String taskType, Pageable pageable);

    @Query("""
            select t.taskStatus as status, count(t) as count
            from TaskMainEntity t
            where (:taskStatus = '' or t.taskStatus like concat('%', :taskStatus, '%'))
              and (:stockCode = '' or t.stockCode like concat('%', :stockCode, '%'))
              and (:taskType = '' or t.taskType like concat('%', :taskType, '%'))
            group by t.taskStatus
            """)
    List<TaskStatusCount> countByStatus(
            @Param("taskStatus") String taskStatus,
            @Param("stockCode") String stockCode,
            @Param("taskType") String taskType);

    List<TaskMainEntity> findTop20ByOrderByCreateTimeDesc();

    interface TaskStatusCount {
        String getStatus();

        long getCount();
    }
}
