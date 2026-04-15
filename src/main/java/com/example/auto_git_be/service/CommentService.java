package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.CommentResponse;
import com.example.auto_git_be.dto.CreateCommentRequest;
import com.example.auto_git_be.entity.Assignment;
import com.example.auto_git_be.entity.Comment;
import com.example.auto_git_be.entity.Student;
import com.example.auto_git_be.entity.StudentAssignment;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.model.CommentStatus;
import com.example.auto_git_be.repository.CommentRepository;
import com.example.auto_git_be.repository.StudentAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final StudentAssignmentRepository studentAssignmentRepository;
    private final AssignmentService assignmentService;
    private final TeacherAssignmentService teacherAssignmentService;

    @Transactional
    public CommentResponse createComment(CreateCommentRequest request, User author) {
        if (request.getAssignmentCode() == null || request.getAssignmentCode().isBlank()) {
            throw new RuntimeException("assignmentCode is required");
        }
        if (request.getTargetBranch() == null || request.getTargetBranch().isBlank()) {
            throw new RuntimeException("targetBranch is required");
        }
        if (request.getStudentFilePath() == null || request.getStudentFilePath().isBlank()) {
            throw new RuntimeException("studentFilePath is required");
        }
        if (request.getComment() == null || request.getComment().isBlank()) {
            throw new RuntimeException("comment is required");
        }

        Assignment assignment = assignmentService.getAssignmentByCode(request.getAssignmentCode());

        if (author.getRole() == User.UserRole.TEACHER && !teacherAssignmentService.hasAccess(author, assignment)) {
            throw new RuntimeException("Not authorized to comment on this assignment");
        }

        StudentAssignment studentAssignment = studentAssignmentRepository
                .findByAssignmentAndBranchName(assignment, request.getTargetBranch())
                .orElseThrow(() -> new RuntimeException("Student assignment not found for targetBranch"));

        Student student = studentAssignment.getStudent();

        Comment saved = commentRepository.save(Comment.builder()
                .assignment(assignment)
                .student(student)
                .author(author)
                .targetBranch(request.getTargetBranch())
                .studentFilePath(normalizePath(request.getStudentFilePath()))
                .teacherFilePath(normalizePath(request.getFilePath()))
                .startLine(request.getStartLine())
                .startColumn(request.getStartColumn())
                .endLine(request.getEndLine())
                .endColumn(request.getEndColumn())
                .selectedText(request.getSelectedText())
                .content(request.getComment())
                .status(CommentStatus.OPEN)
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByFile(
            String assignmentCode,
            String targetBranch,
            String studentFilePath,
            User currentUser
    ) {
        if (assignmentCode == null || assignmentCode.isBlank()) {
            throw new RuntimeException("assignmentCode is required");
        }
        if (targetBranch == null || targetBranch.isBlank()) {
            throw new RuntimeException("targetBranch is required");
        }
        if (studentFilePath == null || studentFilePath.isBlank()) {
            throw new RuntimeException("studentFilePath is required");
        }

        Assignment assignment = assignmentService.getAssignmentByCode(assignmentCode);
        StudentAssignment studentAssignment = studentAssignmentRepository
                .findByAssignmentAndBranchName(assignment, targetBranch)
                .orElseThrow(() -> new RuntimeException("Student assignment not found for targetBranch"));

        Student student = studentAssignment.getStudent();

        if (currentUser.getRole() == User.UserRole.TEACHER) {
            if (!teacherAssignmentService.hasAccess(currentUser, assignment)) {
                throw new RuntimeException("Not authorized to view comments in this assignment");
            }
        } else if (currentUser.getRole() == User.UserRole.STUDENT) {
            if (!student.getUser().getId().equals(currentUser.getId())) {
                throw new RuntimeException("Not authorized to view another student's comments");
            }
        }

        List<Comment> comments = commentRepository
                .findByAssignmentAndStudentAndStudentFilePathAndStatusOrderByCreatedAtAsc(
                        assignment,
                        student,
                        normalizePath(studentFilePath),
                        CommentStatus.OPEN
                );

        return comments.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CommentResponse resolveComment(Long commentId, User currentUser) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        Assignment assignment = comment.getAssignment();

        boolean canResolve = false;
        if (currentUser.getRole() == User.UserRole.TEACHER) {
            canResolve = teacherAssignmentService.hasAccess(currentUser, assignment);
        } else if (currentUser.getRole() == User.UserRole.STUDENT) {
            canResolve = comment.getStudent().getUser().getId().equals(currentUser.getId());
        }

        if (!canResolve) {
            throw new RuntimeException("Not authorized to resolve this comment");
        }

        comment.setStatus(CommentStatus.DELETED);
        Comment saved = commentRepository.save(comment);
        return toResponse(saved);
    }

    private String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        return path.replace('\\', '/');
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .assignmentId(comment.getAssignment().getId())
                .studentId(comment.getStudent().getId())
                .targetBranch(comment.getTargetBranch())
                .studentFilePath(comment.getStudentFilePath())
                .filePath(comment.getTeacherFilePath())
                .startLine(comment.getStartLine())
                .startColumn(comment.getStartColumn())
                .endLine(comment.getEndLine())
                .endColumn(comment.getEndColumn())
                .selectedText(comment.getSelectedText())
                .content(comment.getContent())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getName())
                .status(comment.getStatus())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
