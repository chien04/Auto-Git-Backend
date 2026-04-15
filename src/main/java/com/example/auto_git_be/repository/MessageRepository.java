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

		List<Message> findByClassRoomAndTypeOrderByCreatedAtAsc(ClassRoom classRoom, MessageType type);

		@Query("""
				SELECT m FROM Message m
				WHERE m.type = :type
					AND ((m.sender.id = :userId1 AND m.receiver.id = :userId2)
						OR (m.sender.id = :userId2 AND m.receiver.id = :userId1))
				ORDER BY m.createdAt ASC
		""")
		List<Message> findPrivateMessagesBetweenUsers(
						@Param("userId1") Long userId1,
						@Param("userId2") Long userId2,
						@Param("type") MessageType type
		);

		@Query("""
				SELECT m FROM Message m
				WHERE m.type = com.example.auto_git_be.model.MessageType.PRIVATE
					AND (m.sender.id = :userId OR m.receiver.id = :userId)
				ORDER BY m.createdAt DESC
		""")
		List<Message> findPrivateMessagesByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

		@Query("""
				SELECT COUNT(m) FROM Message m
				WHERE m.type = com.example.auto_git_be.model.MessageType.PRIVATE
					AND m.sender.id = :senderId
					AND m.receiver.id = :receiverId
					AND m.isRead = false
		""")
		long countUnreadPrivateFromUserToUser(
						@Param("senderId") Long senderId,
						@Param("receiverId") Long receiverId
		);

}
