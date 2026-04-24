package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.AssignmentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentTaskRepository extends JpaRepository<AssignmentTask, Long> {

    List<AssignmentTask> findByAssignmentOrderByOrderNoAscIdAsc(Assignment assignment);

    Optional<AssignmentTask> findByAssignmentAndTaskName(Assignment assignment, String taskName);

    Optional<AssignmentTask> findByAssignmentAndOrderNo(Assignment assignment, Integer orderNo);
}