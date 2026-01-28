package com.example.auto_git_be.scheduler;

import com.example.auto_git_be.service.DeadlineReminderService;
import jakarta.annotation.PostConstruct;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MasterDeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(MasterDeadlineScheduler.class);

    private final DeadlineReminderService deadlineReminderService;

    public MasterDeadlineScheduler(DeadlineReminderService deadlineReminderService) {
        this.deadlineReminderService = deadlineReminderService;
    }

    @PostConstruct
    public void init() throws SchedulerException {
        deadlineReminderService.scheduleAllDeadlineReminders();
    }

    @Scheduled(cron = "0 0 0 * * ?") // Chạy lúc 00:00 hằng ngày
    public void dailyRunning() throws SchedulerException {
        deadlineReminderService.scheduleAllDeadlineReminders();
    }
}
