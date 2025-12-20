package com.example.auto_git_be.scheduler;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.repository.ClassRoomRepository;
import com.example.auto_git_be.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommitCountUpdater {

    private static final Logger log = LoggerFactory.getLogger(CommitCountUpdater.class);

    private final StudentService studentService;
    private final ClassRoomRepository classRoomRepository;

    public CommitCountUpdater(StudentService studentService,
                              ClassRoomRepository classRoomRepository) {
        this.studentService = studentService;
        this.classRoomRepository = classRoomRepository;
    }

    /**
     * Update commit counts for all students every 10 minutes
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    public void updateAllCommitCounts() {
        log.info("=== Starting commit count update for all students ===");

        try {
            // Get all active classrooms
            List<ClassRoom> classrooms = classRoomRepository.findAll();

            int updatedCount = 0;
            int errorCount = 0;

            for (ClassRoom classroom : classrooms) {
                if (!classroom.getIsActive()) {
                    continue;
                }

                List<Student> students = studentService.findByClassRoom(classroom);

                for (Student student : students) {
                    try {
                        Integer oldCount = student.getCommitCount();
                        
                        // Update commit count using StudentService
                        Student updatedStudent = studentService.updateCommitCount(student, classroom);
                        
                        // Only count as updated if value actually changed
                        if (oldCount == null || !oldCount.equals(updatedStudent.getCommitCount())) {
                            updatedCount++;
                            log.debug("Updated commit count for {}: {} -> {}", 
                                    student.getStudentName(), oldCount, updatedStudent.getCommitCount());
                        }

                    } catch (Exception e) {
                        log.error("Failed to update commit count for student {}: {}", 
                                student.getStudentName(), e.getMessage());
                        errorCount++;
                    }
                }
            }

            log.info("Commit count update completed: {} updated, {} errors", updatedCount, errorCount);

        } catch (Exception e) {
            log.error("Error in commit count update job: {}", e.getMessage(), e);
        }
    }
}
