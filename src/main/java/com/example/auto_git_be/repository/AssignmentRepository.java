package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.ClassRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    Optional<Assignment> findByAssignmentCode(String assignmentCode);
    Optional<Assignment> findByRepoUrl(String repoUrl);
    List<Assignment> findByClassRoom(ClassRoom classRoom);
    List<Assignment> findByClassRoomAndIsActive(ClassRoom classRoom, Boolean isActive);
    boolean existsByAssignmentCode(String assignmentCode);
    List<Assignment> findByDeadlineBetween(LocalDateTime start, LocalDateTime end);
}
