package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.job.DeadlineReminderJob;
import com.example.auto_git_be.repository.ClassRoomRepository;
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
    private final ClassRoomRepository classRoomRepository;

    public DeadlineReminderService(Scheduler scheduler, ClassRoomRepository classRoomRepository) {
        this.scheduler = scheduler;
        this.classRoomRepository = classRoomRepository;
    }

    /**
     * Schedule reminder jobs cho tất cả các lớp có deadline trong ngày
     */
    public void scheduleAllDeadlineReminders() throws SchedulerException {
        log.info("=== Scheduling Deadline Reminders ===");

        // Xóa tất cả các job cũ
        clearAllJobs();

        // Lấy ngày hiện tại
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        // Query các lớp có deadline trong ngày hôm nay
        List<ClassRoom> classesWithDeadlineToday = classRoomRepository
                .findByDeadlineBetween(startOfDay, endOfDay);

        log.info("Found {} classes with deadline today", classesWithDeadlineToday.size());

        // Với mỗi lớp, tạo một job chạy trước deadline 3 tiếng
        for (ClassRoom classRoom : classesWithDeadlineToday) {
            scheduleReminderJob(classRoom);
        }
    }

    /**
     * Xóa tất cả các job reminder cũ
     */
    private void clearAllJobs() throws SchedulerException {
        GroupMatcher<JobKey> groupMatcher = GroupMatcher.jobGroupEquals(GROUP_NAME);
        Set<JobKey> jobKeys = scheduler.getJobKeys(groupMatcher);

        for (JobKey jobKey : jobKeys) {
            scheduler.deleteJob(jobKey);
            log.info("Deleted old job: {}", jobKey.getName());
        }
    }

    /**
     * Schedule một job reminder cho một lớp cụ thể
     */
    public void scheduleReminderJob(ClassRoom classRoom) throws SchedulerException {
        LocalDateTime deadline = classRoom.getDeadline();
        
        if (deadline == null) {
            log.warn("Class {} has no deadline set", classRoom.getClassCode());
            return;
        }

        LocalDateTime reminderTime = deadline.minusMinutes(2);

        // Kiểm tra nếu thời gian reminder đã qua thì không tạo job
        if (reminderTime.isBefore(LocalDateTime.now())) {
            log.warn("Reminder time for class {} has passed, skipping", classRoom.getClassCode());
            return;
        }

        String jobId = "reminder_" + classRoom.getClassCode();
        JobKey jobKey = new JobKey(jobId, GROUP_NAME);
        TriggerKey triggerKey = new TriggerKey("trigger_" + jobId, GROUP_NAME);

        // Truyền dữ liệu cho job
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("classId", classRoom.getId());
        jobDataMap.put("classCode", classRoom.getClassCode());

        // Tạo JobDetail
        JobDetail jobDetail = JobBuilder.newJob(DeadlineReminderJob.class)
                .withIdentity(jobKey)
                .usingJobData(jobDataMap)
                .build();

        // Tạo trigger với thời gian cụ thể
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .startAt(Date.from(reminderTime.atZone(ZoneId.systemDefault()).toInstant()))
                .build();

        scheduler.scheduleJob(jobDetail, trigger);

        log.info("Scheduled reminder for class {} at {} (deadline: {})",
                classRoom.getClassCode(), reminderTime, deadline);
    }

    /**
     * Xóa job reminder cho một lớp cụ thể
     */
    public void cancelReminderJob(String classCode) throws SchedulerException {
        String jobId = "reminder_" + classCode;
        JobKey jobKey = new JobKey(jobId, GROUP_NAME);
        
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("Cancelled reminder job for class {}", classCode);
        }
    }

    /**
     * Lấy danh sách tất cả các job reminder đang chạy
     */
    public Set<JobKey> getAllScheduledJobs() throws SchedulerException {
        GroupMatcher<JobKey> groupMatcher = GroupMatcher.jobGroupEquals(GROUP_NAME);
        return scheduler.getJobKeys(groupMatcher);
    }
}
