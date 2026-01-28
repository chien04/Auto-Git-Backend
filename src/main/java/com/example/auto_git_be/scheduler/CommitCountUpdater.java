package com.example.auto_git_be.scheduler;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.repository.AssignmentRepository;
import com.example.auto_git_be.repository.ClassRoomRepository;
import com.example.auto_git_be.service.StudentAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommitCountUpdater {

    private static final Logger log = LoggerFactory.getLogger(CommitCountUpdater.class);

    private final StudentAssignmentService studentAssignmentService;
    private final AssignmentRepository assignmentRepository;

    public CommitCountUpdater(StudentAssignmentService studentAssignmentService,
                              AssignmentRepository assignmentRepository) {
        this.studentAssignmentService = studentAssignmentService;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Update commit counts for all student assignments every 10 minutes
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    public void updateAllCommitCounts() {
        try {
            // Get all active assignments directly
            List<Assignment> assignments = assignmentRepository.findAll();

            int updatedCount = 0;
            int errorCount = 0;

            for (Assignment assignment : assignments) {
                if (!assignment.getIsActive()) {
                    continue;
                }
                
                // Get all student assignments for this assignment
                List<StudentAssignment> studentAssignments = studentAssignmentService.findByAssignment(assignment);

                for (StudentAssignment studentAssignment : studentAssignments) {
                        try {
                            Integer oldCount = studentAssignment.getCommitCount();
                            
                            // Update commit count using StudentAssignmentService
                            StudentAssignment updated = studentAssignmentService.updateCommitCount(studentAssignment);
                            
                            // Only count as updated if value actually changed
                            if (oldCount == null || !oldCount.equals(updated.getCommitCount())) {
                                updatedCount++;
                            }

                        } catch (Exception e) {
                            errorCount++;
                        }
                    }
                }

        } catch (Exception e) {
        }
    }
}
