package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.StudentRepository;
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
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private GitHubService githubService;

    /**
     * Update commit count for a student from GitHub
     */
    public Student updateCommitCount(Student student, ClassRoom classroom) throws Exception {
        try {
            log.info("Updating commit count for student: {} in class: {}", 
                    student.getStudentName(), classroom.getClassCode());
            
            // Get repo full name
            String repoUrl = classroom.getRepoUrl();
            String repoFullName = repoUrl
                    .replace("https://github.com/", "")
                    .replace(".git", "")
                    .trim();
            
            log.info("Repo: {}, Branch: {}", repoFullName, student.getBranchName());
            
            // Get commit count from GitHub
            int commitCount = githubService.getCommitCount(repoFullName, student.getBranchName());
            
            log.info("GitHub API returned commit count: {}", commitCount);
            
            // Update student
            Integer oldCount = student.getCommitCount();
            student.setCommitCount(commitCount);
            
            // Update lastCommitAt if there are new commits
            if (commitCount > 0 && (oldCount == null || commitCount > oldCount)) {
                student.setLastCommitAt(LocalDateTime.now());
                log.info("Updated lastCommitAt for student: {}", student.getStudentName());
            }
            
            Student savedStudent = studentRepository.save(student);
            log.info("Successfully updated student {} commit count from {} to {}", 
                    student.getStudentName(), oldCount, commitCount);
            
            return savedStudent;
            
        } catch (Exception e) {
            log.error("Failed to update commit count for student {}: {}", 
                    student.getStudentName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get student by user and classroom
     */
    public Optional<Student> findByUserAndClassRoom(User user, ClassRoom classRoom) {
        return studentRepository.findByUserAndClassRoom(user, classRoom);
    }

    /**
     * Get all students in a classroom
     */
    public List<Student> findByClassRoom(ClassRoom classRoom) {
        return studentRepository.findByClassRoom(classRoom);
    }

    /**
     * Save student
     */
    public Student save(Student student) {
        return studentRepository.save(student);
    }

    /**
     * Delete student
     */
    public void delete(Student student) {
        studentRepository.delete(student);
    }

    /**
     * Check if student exists in classroom
     */
    public boolean existsByUserAndClassRoom(User user, ClassRoom classRoom) {
        return studentRepository.existsByUserAndClassRoom(user, classRoom);
    }

    /**
     * Get all classes a student is enrolled in
     */
    public List<Student> findByUser(User user) {
        return studentRepository.findByUser(user);
    }

    /**
     * Get commit activity by user for the last 28 days
     * Returns a map of date -> number of commits on that day
     */
    public Map<LocalDate, Integer> getCommitActivityByUser(User user) {
        Map<java.time.LocalDate, Integer> activityMap = new HashMap<>();
        
        // Initialize last 28 days with 0 commits
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 27; i >= 0; i--) {
            java.time.LocalDate date = today.minusDays(i);
            activityMap.put(date, 0);
        }

        // Get all enrollments for this user
        List<Student> enrollments = studentRepository.findByUser(user);

        // For each enrollment, get commit history from GitHub
        for (Student student : enrollments) {
            try {
                ClassRoom classRoom = student.getClassRoom();
                String repoName = classRoom.getRepoName();
                String branchName = student.getBranchName();

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
                System.err.println("Failed to get commit activity for student " + student.getId() + ": " + e.getMessage());
            }
        }

        return activityMap;
    }
}
