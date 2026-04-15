package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.Comment;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.model.CommentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByAssignmentAndStudentAndStudentFilePathAndStatusOrderByCreatedAtAsc(
            Assignment assignment,
            Student student,
            String studentFilePath,
            CommentStatus status
    );

    List<Comment> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
}
