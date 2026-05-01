package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.TeacherAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.TeacherAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TeacherAssignmentService {

    private final TeacherAssignmentRepository teacherAssignmentRepository;

    public Optional<TeacherAssignment> getTeacherAssignment(User teacher, Assignment assignment) {
        return teacherAssignmentRepository.findByTeacherAndAssignment(teacher, assignment);
    }

    public List<TeacherAssignment> getTeacherAssignment(Assignment assignment) {
        return teacherAssignmentRepository.findByAssignment(assignment);
    }

    public boolean hasAccess(User teacher, Assignment assignment) {
        if (assignment.getClassRoom().getTeacher().getId().equals(teacher.getId())) {
            return true;
        }
        return teacherAssignmentRepository.existsByTeacherAndAssignment(teacher, assignment);
    }

}
