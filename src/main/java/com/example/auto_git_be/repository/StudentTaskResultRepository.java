package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.StudentTaskResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentTaskResultRepository extends JpaRepository<StudentTaskResult, Long> {
    List<StudentTaskResult> findByStudentAssignment(StudentAssignment studentAssignment);
}
