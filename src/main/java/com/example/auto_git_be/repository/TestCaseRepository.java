package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.AssignmentTask;
import com.example.auto_git_be.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    void deleteAllByAssignmentTaskAndIsSampleFalse(AssignmentTask assignmentTask);
    List<TestCase> findByAssignmentTaskAndIsSampleFalse(AssignmentTask assignmentTask);
    List<TestCase> findByAssignmentTaskAndIsSampleTrue(AssignmentTask assignmentTask);
}
