package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;

    private final GitHubService githubService;

    public Optional<Student> findByUserAndClassRoom(User user, ClassRoom classRoom) {
        return studentRepository.findByUserAndClassRoom(user, classRoom);
    }

    public List<Student> findByClassRoom(ClassRoom classRoom) {
        return studentRepository.findByClassRoom(classRoom);
    }

    public List<Student> findByUser(User user) {
        return studentRepository.findByUser(user);
    }

    public Map<LocalDate, Integer> getCommitActivityByUser(User user) {
        Map<java.time.LocalDate, Integer> activityMap = new HashMap<>();
        
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 27; i >= 0; i--) {
            java.time.LocalDate date = today.minusDays(i);
            activityMap.put(date, 0);
        }

        List<Student> enrollments = studentRepository.findByUser(user);

        for (Student student : enrollments) {
            try {
                // Get all assignments this student is enrolled in
                List<StudentAssignment> studentAssignments = student.getAssignments();
                
                for (StudentAssignment studentAssignment : studentAssignments) {
                    try {
                        // Get assignment and repository info
                        com.example.auto_git_be.entity.Assignment assignment = studentAssignment.getAssignment();
                        String repoName = assignment.getRepoName();
                        String branchName = studentAssignment.getBranchName();

                        // Get commits from GitHub API
                        List<org.kohsuke.github.GHCommit> commits = githubService.getCommits(repoName, branchName);

                        // Count commits per day
                        for (org.kohsuke.github.GHCommit commit : commits) {
                            // Get commit date
                            java.util.Date commitDate = commit.getCommitDate();
                            java.time.LocalDate commitLocalDate = commitDate.toInstant()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate();

                            // Only count commits in last 28 days
                            if (!commitLocalDate.isBefore(today.minusDays(27)) && !commitLocalDate.isAfter(today)) {
                                activityMap.put(commitLocalDate, activityMap.getOrDefault(commitLocalDate, 0) + 1);
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
            }
        }

        return activityMap;
    }
}
