package com.example.auto_git_be.job;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.repository.AssignmentRepository;
import com.example.auto_git_be.repository.StudentAssignmentRepository;
import com.example.auto_git_be.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class DeadlineReminderJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderJob.class);

    private final AssignmentRepository assignmentRepository;
    private final StudentAssignmentRepository studentAssignmentRepository;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        JobDataMap jobDataMap = jobExecutionContext.getMergedJobDataMap();
        Long assignmentId = jobDataMap.getLong("assignmentId");
        String assignmentCode = jobDataMap.getString("assignmentCode");

        EmailService emailService = (EmailService) jobDataMap.get("emailService");

        try {
            Assignment assignment = assignmentRepository.findById(assignmentId)
                    .orElseThrow(() -> new RuntimeException("Assignment not found: " + assignmentId));

            List<StudentAssignment> studentAssignments = studentAssignmentRepository.findByAssignment(assignment);

            int emailsSent = 0;

            for (StudentAssignment studentAssignment : studentAssignments) {
                if (shouldSendReminder(studentAssignment)) {
                    sendReminderEmail(studentAssignment, assignment, emailService);
                    emailsSent++;
                }
            }

        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }

    private boolean shouldSendReminder(StudentAssignment studentAssignment) {
        Integer commitCount = studentAssignment.getCommitCount();
        return commitCount == null || commitCount <= 1;
    }

    private void sendReminderEmail(StudentAssignment studentAssignment, Assignment assignment, EmailService emailService) {
        String toEmail = studentAssignment.getStudent().getUser().getEmail();
        String subject = "Nhắc nhở: Deadline bài tập " + assignment.getTitle() + " sắp hết hạn";

        String body = String.format(
                "Xin chào %s,\n\n" +
                        "Đây là email nhắc nhở về deadline của bài tập:\n" +
                        "- Tên bài tập: %s\n" +
                        "- Mã bài tập: %s\n" +
                        "- Lớp: %s\n" +
                        "- Deadline: %s\n\n" +
                        "Hệ thống nhận thấy bạn chưa có commit code nào trong repository.\n" +
                        "Vui lòng hoàn thành bài tập và commit code trước thời hạn.\n\n" +
                        "Lưu ý: Bạn chỉ nhận được email này một lần duy nhất.\n\n" +
                        "Trân trọng,\n" +
                        "Auto Git Classroom System",
                studentAssignment.getStudent().getStudentName(),
                assignment.getTitle(),
                assignment.getAssignmentCode(),
                assignment.getClassRoom().getName(),
                assignment.getDeadline()
        );

        emailService.sendEmail(toEmail, subject, body);

    }
}
