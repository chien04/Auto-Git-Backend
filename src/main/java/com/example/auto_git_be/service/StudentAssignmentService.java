package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.repository.StudentAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class StudentAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(StudentAssignmentService.class);

    @Autowired
    private StudentAssignmentRepository studentAssignmentRepository;

    @Autowired
    private GitHubService githubService;

    /**
     * Update commit count for a student assignment from GitHub
     */
    @Transactional
    public StudentAssignment updateCommitCount(StudentAssignment studentAssignment) throws Exception {
        try {
            Assignment assignment = studentAssignment.getAssignment();
            Student student = studentAssignment.getStudent();
            
            // Get repo full name from assignment
            String repoUrl = assignment.getRepoUrl();
            String repoFullName = repoUrl
                    .replace("https://github.com/", "")
                    .replace(".git", "")
                    .trim();
            
            // Get commit count from GitHub
            int commitCount = githubService.getCommitCount(repoFullName, studentAssignment.getBranchName());
            
            // Update student assignment
            Integer oldCount = studentAssignment.getCommitCount();
            studentAssignment.setCommitCount(commitCount);
            
            // Update lastCommitAt if there are new commits
            if (commitCount > 0 && (oldCount == null || commitCount > oldCount)) {
                studentAssignment.setLastCommitAt(LocalDateTime.now());
            }
            
            StudentAssignment saved = studentAssignmentRepository.save(studentAssignment);
            
            return saved;
            
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Get student assignment by student and assignment
     */
    public Optional<StudentAssignment> findByStudentAndAssignment(Student student, Assignment assignment) {
        return studentAssignmentRepository.findByStudentAndAssignment(student, assignment);
    }

    /**
     * Get all student assignments for a student
     */
    public List<StudentAssignment> findByStudent(Student student) {
        return studentAssignmentRepository.findByStudent(student);
    }

    /**
     * Get all student assignments for an assignment
     */
    public List<StudentAssignment> findByAssignment(Assignment assignment) {
        return studentAssignmentRepository.findByAssignment(assignment);
    }

    /**
     * Save student assignment
     */
    public StudentAssignment save(StudentAssignment studentAssignment) {
        return studentAssignmentRepository.save(studentAssignment);
    }

    /**
     * Delete student assignment
     */
    public void delete(StudentAssignment studentAssignment) {
        studentAssignmentRepository.delete(studentAssignment);
    }

    /**
     * Check if student assignment exists
     */
    public boolean existsByStudentAndAssignment(Student student, Assignment assignment) {
        return studentAssignmentRepository.existsByStudentAndAssignment(student, assignment);
    }
}
