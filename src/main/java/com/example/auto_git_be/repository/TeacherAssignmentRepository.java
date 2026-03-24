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

    Optional<TeacherAssignment> findByTeacherAndAssignment(User teacher, Assignment assignment);
    List<TeacherAssignment> findByTeacher(User teacher);
    List<TeacherAssignment> findByAssignment(Assignment assignment);
    boolean existsByTeacherAndAssignment(User teacher, Assignment assignment);
}
