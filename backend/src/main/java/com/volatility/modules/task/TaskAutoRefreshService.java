package com.volatility.modules.task;

import com.volatility.common.enums.TaskStatus;
import com.volatility.common.util.JsonUtils;
import com.volatility.modules.task.entity.TaskMainEntity;
import com.volatility.modules.task.entity.TaskParamSnapshotEntity;
import com.volatility.modules.task.repository.TaskMainRepository;
import com.volatility.modules.task.repository.TaskParamSnapshotRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskAutoRefreshService {

    private static final String AUTO_REFRESH_DAILY_KEY = "autoRefreshDaily";
    private static final String AUTO_REFRESH_SUMMARY_PREFIX = "AKShare 今日行情刷新已提交";
    private static final List<String> RUNNING_STATUSES = List.of(
            TaskStatus.PENDING.name(),
            TaskStatus.FETCHING.name(),
            TaskStatus.CLEANING.name(),
            TaskStatus.CALCULATED.name(),
            TaskStatus.ASSESSED.name()
    );

    private final TaskMainRepository taskMainRepository;
    private final TaskParamSnapshotRepository snapshotRepository;
    private final JsonUtils jsonUtils;
    private final ApplicationEventPublisher eventPublisher;

    public TaskAutoRefreshService(
            TaskMainRepository taskMainRepository,
            TaskParamSnapshotRepository snapshotRepository,
            JsonUtils jsonUtils,
            ApplicationEventPublisher eventPublisher) {
        this.taskMainRepository = taskMainRepository;
        this.snapshotRepository = snapshotRepository;
        this.jsonUtils = jsonUtils;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Shanghai")
    public void scheduledDailyRefresh() {
        System.out.println("===== 定时刷新触发，时间：" + LocalDateTime.now() + " =====");
        refreshAkshareTasksForToday("SCHEDULED");
    }

    @Transactional
    public void refreshAkshareTasksForToday(String triggerSource) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        List<TaskParamSnapshotEntity> snapshots = snapshotRepository.findByDataSourceTypeIgnoreCase("AKSHARE");

        System.out.println("===== 开始自动刷新，快照总数：" + snapshots.size() + " =====");

        for (TaskParamSnapshotEntity snapshot : snapshots) {
            TaskMainEntity task = taskMainRepository.findById(snapshot.getTaskId()).orElse(null);
            String taskNo = task != null ? task.getTaskNo() : "未知";

            if (!snapshot.getDateEnd().isBefore(today)) {
                System.out.println("跳过任务 " + taskNo + "：dateEnd=" + snapshot.getDateEnd() + " 未早于今天");
                continue;
            }

            if (task == null || TaskStatus.ARCHIVED.name().equals(task.getTaskStatus())) {
                System.out.println("跳过任务 " + taskNo + "：任务不存在或已归档");
                continue;
            }

            if (RUNNING_STATUSES.contains(task.getTaskStatus())) {
                System.out.println("跳过任务 " + taskNo + "：当前状态 " + task.getTaskStatus() + " 正在运行");
                continue;
            }

            if (!shouldAutoRefresh(snapshot, task)) {
                System.out.println("跳过任务 " + taskNo + "：autoRefreshDaily=false 或不满足兼容条件");
                continue;
            }

            System.out.println("刷新任务 " + taskNo + "，原 dateEnd=" + snapshot.getDateEnd() + "，更新为 " + today);
            updateSnapshotEndDate(snapshot, today);
            resetTaskForRefresh(task, triggerSource);
            eventPublisher.publishEvent(new TaskExecutionRequestedEvent(task.getTaskNo()));
        }
        System.out.println("===== 自动刷新结束 =====");
    }

    private void updateSnapshotEndDate(TaskParamSnapshotEntity snapshot, LocalDate dateEnd) {
        Map<String, Object> params = new LinkedHashMap<>(jsonUtils.toMap(snapshot.getParamJson()));
        params.put("dateEnd", dateEnd.toString());
        snapshot.setDateEnd(dateEnd);
        snapshot.setParamJson(jsonUtils.toJson(params));
        snapshotRepository.save(snapshot);
    }

    private boolean shouldAutoRefresh(TaskParamSnapshotEntity snapshot, TaskMainEntity task) {
        Map<String, Object> params = jsonUtils.toMap(snapshot.getParamJson());
        Object rawFlag = params.get(AUTO_REFRESH_DAILY_KEY);
        if (rawFlag instanceof Boolean flag) {
            System.out.println("  任务 " + task.getTaskNo() + " autoRefreshDaily=" + flag);
            return flag;
        }
        if (rawFlag instanceof String flagText) {

            return Boolean.parseBoolean(flagText);
        }
        if (task.getResultSummary() != null && task.getResultSummary().startsWith(AUTO_REFRESH_SUMMARY_PREFIX)) {
            return true;
        }
        return snapshot.getCreateTime() != null
                && snapshot.getDateEnd() != null
                && snapshot.getDateEnd().equals(snapshot.getCreateTime().toLocalDate());
    }

    private void resetTaskForRefresh(TaskMainEntity task, String triggerSource) {
        task.setTaskStatus(TaskStatus.PENDING.name());
        task.setProgress(0);
        task.setErrorMsg(null);
        task.setResultSummary("AKShare 今日行情刷新已提交：" + triggerSource);
        task.setStartTime(null);
        task.setEndTime(null);
        task.setUpdateTime(LocalDateTime.now());
        taskMainRepository.save(task);
    }
}
