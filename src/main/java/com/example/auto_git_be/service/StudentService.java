package com.example.auto_git_be.service;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.repository.StudentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;

    private final GitHubService githubService;

    public Optional<Student> findByUserAndClassRoom(User user, ClassRoom classRoom) {
        return studentRepository.findByUserAndClassRoom(user, classRoom);
    }

    public List<Student> findByClassRoom(ClassRoom classRoom) {
        return studentRepository.findByClassRoom(classRoom);
    }

    public List<Student> findByUser(User user) {
        return studentRepository.findByUser(user);
    }

    public Student findById(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Student not found with id " + id));
    }
}
