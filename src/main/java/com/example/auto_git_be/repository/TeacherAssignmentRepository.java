package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.TeacherAssignment;
import com.example.auto_git_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Long> {
    
    /**
     * Find teacher assignment by teacher and assignment
     */
    Optional<TeacherAssignment> findByTeacherAndAssignment(User teacher, Assignment assignment);
    
    /**
     * Find all assignments for a teacher
     */
    List<TeacherAssignment> findByTeacher(User teacher);
    
    /**
     * Find all teachers for an assignment
     */
    List<TeacherAssignment> findByAssignment(Assignment assignment);
    
    /**
     * Check if teacher has access to assignment
     */
    boolean existsByTeacherAndAssignment(User teacher, Assignment assignment);
}
