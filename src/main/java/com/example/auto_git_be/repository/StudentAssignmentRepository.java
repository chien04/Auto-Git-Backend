package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentAssignmentRepository extends JpaRepository<StudentAssignment, Long> {

    List<StudentAssignment> findByAssignmentId(Long assignmentId);
    List<StudentAssignment> findByStudent(Student student);
    List<StudentAssignment> findByAssignment(Assignment assignment);
    Optional<StudentAssignment> findByStudentAndAssignment(Student student, Assignment assignment);
    Optional<StudentAssignment> findByAssignmentAndBranchName(Assignment assignment, String branchName);
    boolean existsByStudentAndAssignment(Student student, Assignment assignment);
}
