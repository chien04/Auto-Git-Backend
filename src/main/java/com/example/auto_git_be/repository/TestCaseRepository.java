package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    
    List<TestCase> findByAssignmentCode(String assignmentCode);
    
    List<TestCase> findByAssignmentCodeAndExerciseName(String assignmentCode, String exerciseName);
    
    void deleteByAssignmentCode(String assignmentCode);
    
    boolean existsByAssignmentCode(String assignmentCode);
}
