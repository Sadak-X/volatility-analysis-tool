package com.volatility.modules.task;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TaskExecutionListener {

    private final TaskService taskService;

    public TaskExecutionListener(TaskService taskService) {
        this.taskService = taskService;
    }

    @Async
    @EventListener
    public void handle(TaskExecutionRequestedEvent event) {
        taskService.process(event.taskNo());
    }
}
