package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.repository.StudentAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(StudentAssignmentService.class);

    private final StudentAssignmentRepository studentAssignmentRepository;
    private final GitHubService githubService;

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

    public Optional<StudentAssignment> findByStudentAndAssignment(Student student, Assignment assignment) {
        return studentAssignmentRepository.findByStudentAndAssignment(student, assignment);
    }

    public List<StudentAssignment> findByAssignment(Assignment assignment) {
        return studentAssignmentRepository.findByAssignment(assignment);
    }
}
