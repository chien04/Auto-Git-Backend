package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.TeacherAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.TeacherAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TeacherAssignmentService {
    
    @Autowired
    private TeacherAssignmentRepository teacherAssignmentRepository;
    
    /**
     * Save or update teacher assignment
     */
    @Transactional
    public TeacherAssignment saveTeacherAssignment(User teacher, Assignment assignment, String localPath, String role) {
        Optional<TeacherAssignment> existing = teacherAssignmentRepository.findByTeacherAndAssignment(teacher, assignment);
        
        if (existing.isPresent()) {
            // Update existing
            TeacherAssignment ta = existing.get();
            if (localPath != null && !localPath.isEmpty()) {
                ta.setLocalPath(localPath);
            }
            if (role != null && !role.isEmpty()) {
                ta.setRole(role);
            }
            TeacherAssignment saved = teacherAssignmentRepository.save(ta);
            return saved;
        } else {
            // Create new
            TeacherAssignment ta = TeacherAssignment.builder()
                    .teacher(teacher)
                    .assignment(assignment)
                    .localPath(localPath)
                    .role(role != null ? role : "MAIN")
                    .build();
            TeacherAssignment saved = teacherAssignmentRepository.save(ta);
            return saved;
        }
    }
    
    /**
     * Get teacher assignment
     */
    public Optional<TeacherAssignment> getTeacherAssignment(User teacher, Assignment assignment) {
        return teacherAssignmentRepository.findByTeacherAndAssignment(teacher, assignment);
    }
    
    /**
     * Get all assignments for teacher
     */
    public List<TeacherAssignment> getTeacherAssignments(User teacher) {
        return teacherAssignmentRepository.findByTeacher(teacher);
    }
    
    /**
     * Check if teacher has access to assignment
     */
    public boolean hasAccess(User teacher, Assignment assignment) {
        // Teacher has access if:
        // 1. They created the assignment (owner of the class)
        // 2. They are added as sub-teacher
        if (assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
            return true;
        }
        return teacherAssignmentRepository.existsByTeacherAndAssignment(teacher, assignment);
    }
    
    /**
     * Get local path for teacher's assignment
     */
    public String getLocalPath(User teacher, Assignment assignment) {
        Optional<TeacherAssignment> ta = teacherAssignmentRepository.findByTeacherAndAssignment(teacher, assignment);
        return ta.map(TeacherAssignment::getLocalPath).orElse(null);
    }
}
