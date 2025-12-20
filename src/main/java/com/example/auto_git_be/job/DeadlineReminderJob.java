package com.example.auto_git_be.job;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.repository.ClassRoomRepository;
import com.example.auto_git_be.repository.StudentRepository;
import com.example.auto_git_be.service.EmailService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class DeadlineReminderJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderJob.class);

    private final ClassRoomRepository classRoomRepository;
    private final StudentRepository studentRepository;
    private final EmailService emailService;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        Long classId = jobExecutionContext.getJobDetail().getJobDataMap().getLong("classId");
        String classCode = jobExecutionContext.getJobDetail().getJobDataMap().getString("classCode");

        log.info("=== Executing Deadline Reminder Job for class: {} ===", classCode);

        try {
            // Lấy thông tin lớp
            ClassRoom classRoom = classRoomRepository.findById(classId)
                    .orElseThrow(() -> new RuntimeException("ClassRoom not found: " + classId));

            // Lấy tất cả sinh viên trong lớp
            List<Student> students = studentRepository.findByClassRoom(classRoom);

            log.info("Found {} students in class {}", students.size(), classCode);

            int emailsSent = 0;

            // Kiểm tra từng sinh viên
            for (Student student : students) {
                // Kiểm tra: chưa commit lần nào (hoặc chỉ có commit init) và chưa gửi email
                if (shouldSendReminder(student)) {
                    sendReminderEmail(student, classRoom);
                    emailsSent++;
                }
            }

            log.info("Sent {} reminder emails for class {}", emailsSent, classCode);

        } catch (Exception e) {
            log.error("Error executing reminder job for class {}: {}", classCode, e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }

    /**
     * Kiểm tra xem có nên gửi email nhắc nhở cho sinh viên này không
     */
    private boolean shouldSendReminder(Student student) {
        // Chưa commit lần nào HOẶC chỉ có 1 commit (init repo)
        Integer commitCount = student.getCommitCount();
        if (commitCount == null || commitCount <= 1) {
            log.debug("Student {} has {} commits, needs reminder", 
                    student.getStudentName(), commitCount);
            return true;
        }

        log.debug("Student {} has {} commits, no reminder needed", 
                student.getStudentName(), commitCount);
        return false;
    }

    /**
     * Gửi email nhắc nhở cho sinh viên
     */
    private void sendReminderEmail(Student student, ClassRoom classRoom) {
        try {
            String toEmail = student.getUser().getEmail();
            String subject = "Nhắc nhở: Deadline lớp " + classRoom.getName() + " sắp hết hạn";
            
            String body = String.format(
                    "Xin chào %s,\n\n" +
                    "Đây là email nhắc nhở về deadline của lớp học:\n" +
                    "- Tên lớp: %s\n" +
                    "- Mã lớp: %s\n" +
                    "- Deadline: %s\n\n" +
                    "Hệ thống nhận thấy bạn chưa có commit code nào trong repository.\n" +
                    "Vui lòng hoàn thành bài tập và commit code trước thời hạn.\n\n" +
                    "Lưu ý: Bạn chỉ nhận được email này một lần duy nhất.\n\n" +
                    "Trân trọng,\n" +
                    "Auto Git Classroom System",
                    student.getStudentName(),
                    classRoom.getName(),
                    classRoom.getClassCode(),
                    classRoom.getDeadline()
            );

            emailService.sendEmail(toEmail, subject, body);

            log.info("Reminder email sent to student: {} ({})", 
                    student.getStudentName(), toEmail);

        } catch (Exception e) {
            log.error("Failed to send reminder email to student {}: {}", 
                    student.getStudentName(), e.getMessage());
            // Không throw exception để không làm dừng job xử lý các sinh viên khác
        }
    }
}
