package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.job.DeadlineReminderJob;
import com.example.auto_git_be.repository.AssignmentRepository;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class DeadlineReminderService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);
    private static final String GROUP_NAME = "DEADLINE_REMINDER";

    private final Scheduler scheduler;
    private final AssignmentRepository assignmentRepository;
    private final EmailService emailService;

    public DeadlineReminderService(Scheduler scheduler, AssignmentRepository assignmentRepository, EmailService emailService) {
        this.scheduler = scheduler;
        this.assignmentRepository = assignmentRepository;
        this.emailService = emailService;
    }

    public void scheduleAllDeadlineReminders() throws SchedulerException {
        clearAllJobs();

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        List<Assignment> assignmentsWithDeadlineToday = assignmentRepository
                .findByDeadlineBetween(startOfDay, endOfDay);

        for (Assignment assignment : assignmentsWithDeadlineToday) {
            scheduleReminderJob(assignment);
        }
    }

    private void clearAllJobs() throws SchedulerException {
        GroupMatcher<JobKey> groupMatcher = GroupMatcher.jobGroupEquals(GROUP_NAME);
        Set<JobKey> jobKeys = scheduler.getJobKeys(groupMatcher);

        for (JobKey jobKey : jobKeys) {
            scheduler.deleteJob(jobKey);
        }
    }

    public void scheduleReminderJob(Assignment assignment) throws SchedulerException {
        LocalDateTime deadline = assignment.getDeadline();
        
        if (deadline == null) {
            return;
        }

        LocalDateTime reminderTime = deadline.minusMinutes(1);

        if (reminderTime.isBefore(LocalDateTime.now())) {
            return;
        }

        String jobId = "reminder_" + assignment.getAssignmentCode();
        JobKey jobKey = new JobKey(jobId, GROUP_NAME);
        TriggerKey triggerKey = new TriggerKey("trigger_" + jobId, GROUP_NAME);

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("assignmentId", assignment.getId());
        jobDataMap.put("assignmentCode", assignment.getAssignmentCode());
        jobDataMap.put("emailService", emailService);
        
        JobDetail jobDetail = JobBuilder.newJob(DeadlineReminderJob.class)
                .withIdentity(jobKey)
                .usingJobData(jobDataMap)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startAt(Date.from(reminderTime.atZone(ZoneId.systemDefault()).toInstant()))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
    }

    public void cancelReminderJob(String assignmentCode) throws SchedulerException {
        String jobId = "reminder_" + assignmentCode;
        JobKey jobKey = new JobKey(jobId, GROUP_NAME);
        
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }
    }

    public Set<JobKey> getAllScheduledJobs() throws SchedulerException {
        GroupMatcher<JobKey> groupMatcher = GroupMatcher.jobGroupEquals(GROUP_NAME);
        return scheduler.getJobKeys(groupMatcher);
    }
}
