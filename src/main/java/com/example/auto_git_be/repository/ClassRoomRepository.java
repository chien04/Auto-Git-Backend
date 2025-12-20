package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassRoomRepository extends JpaRepository<ClassRoom, Long> {
    Optional<ClassRoom> findByClassCode(String classCode);
    List<ClassRoom> findByTeacher(User teacher);
    List<ClassRoom> findByTeacherAndIsActive(User teacher, Boolean isActive);
    boolean existsByClassCode(String classCode);
    List<ClassRoom> findByDeadlineBetween(LocalDateTime start, LocalDateTime end);
}
