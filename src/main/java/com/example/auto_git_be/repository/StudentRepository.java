package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findByClassRoom(ClassRoom classRoom);
    List<Student> findByUser(User user);
    Optional<Student> findByUserAndClassRoom(User user, ClassRoom classRoom);
    boolean existsByUserAndClassRoom(User user, ClassRoom classRoom);
}
