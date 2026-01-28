package com.example.auto_git_be.repository;

import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Message;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.model.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    // Find private messages between two users
    @Query("SELECT m FROM Message m WHERE m.type = 'PRIVATE' " +
           "AND ((m.sender.id = :userId1 AND m.receiver.id = :userId2) " +
           "OR (m.sender.id = :userId2 AND m.receiver.id = :userId1)) " +
           "ORDER BY m.createdAt ASC")
    List<Message> findPrivateMessages(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    // Find all messages in a classroom group
    List<Message> findByClassRoomAndTypeOrderByCreatedAtAsc(ClassRoom classRoom, MessageType type);
    
    // Count unread messages for a user
    int countByReceiverAndIsReadFalse(User receiver);
    
    // Count unread private messages from a specific user
    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiver.id = :receiverId " +
           "AND m.sender.id = :senderId AND m.isRead = false AND m.type = 'PRIVATE'")
    int countUnreadPrivateMessages(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);
    
    // Find all unread messages for a user
    List<Message> findByReceiverAndIsReadFalseOrderByCreatedAtDesc(User receiver);
}
