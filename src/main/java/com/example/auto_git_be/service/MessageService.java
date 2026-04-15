package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.ChatMessageDTO;
import com.example.auto_git_be.entity.ClassRoom;
import com.example.auto_git_be.entity.Message;
import com.example.auto_git_be.entity.User;
import com.example.auto_git_be.model.MessageType;
import com.example.auto_git_be.repository.ClassRoomRepository;
import com.example.auto_git_be.repository.MessageRepository;
import com.example.auto_git_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MessageService {

	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final ClassRoomRepository classRoomRepository;

	@Transactional
	public ChatMessageDTO sendMessage(Long senderId,
									  Long receiverId,
									  Long classroomId,
									  String content,
									  MessageType type) {
		User sender = userRepository.findById(senderId)
				.orElseThrow(() -> new RuntimeException("Sender not found: " + senderId));

		User receiver = null;
		ClassRoom classRoom = null;

		if (type == MessageType.PRIVATE) {
			if (receiverId == null) {
				throw new RuntimeException("receiverId is required for private message");
			}
			receiver = userRepository.findById(receiverId)
					.orElseThrow(() -> new RuntimeException("Receiver not found: " + receiverId));
		} else if (type == MessageType.CLASS_GROUP) {
			if (classroomId == null) {
				throw new RuntimeException("classroomId is required for class group message");
			}
			classRoom = classRoomRepository.findById(classroomId)
					.orElseThrow(() -> new RuntimeException("Classroom not found: " + classroomId));
		}

		Message message = Message.builder()
				.sender(sender)
				.receiver(receiver)
				.classRoom(classRoom)
				.content(content)
				.type(type)
				.isRead(false)
				.build();

		Message saved = messageRepository.save(message);
		return toDTO(saved);
	}

	@Transactional(readOnly = true)
	public List<ChatMessageDTO> getPrivateMessages(Long currentUserId, Long otherUserId) {
		List<Message> messages = messageRepository.findPrivateMessagesBetweenUsers(
				currentUserId,
				otherUserId,
				MessageType.PRIVATE
		);
		return messages.stream().map(this::toDTO).toList();
	}

	@Transactional(readOnly = true)
	public List<ChatMessageDTO> getClassMessages(Long classroomId) {
		ClassRoom classRoom = classRoomRepository.findById(classroomId)
				.orElseThrow(() -> new RuntimeException("Classroom not found: " + classroomId));
		List<Message> messages = messageRepository.findByClassRoomAndTypeOrderByCreatedAtAsc(
				classRoom,
				MessageType.CLASS_GROUP
		);
		return messages.stream().map(this::toDTO).toList();
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> getRecentPrivateChats(Long currentUserId) {
		List<Message> all = messageRepository.findPrivateMessagesByUserIdOrderByCreatedAtDesc(currentUserId);

		Map<Long, Map<String, Object>> latestByOtherUser = new HashMap<>();
		for (Message message : all) {
			User otherUser = message.getSender().getId().equals(currentUserId)
					? message.getReceiver()
					: message.getSender();

			if (otherUser == null || latestByOtherUser.containsKey(otherUser.getId())) {
				continue;
			}

			long unreadCount = messageRepository.countUnreadPrivateFromUserToUser(otherUser.getId(), currentUserId);

			Map<String, Object> item = new HashMap<>();
			item.put("userId", otherUser.getId());
			item.put("userName", otherUser.getName());
			item.put("userEmail", otherUser.getEmail());
			item.put("lastMessage", message.getContent());
			item.put("lastMessageTime", message.getCreatedAt() != null ? message.getCreatedAt().toString() : null);
			item.put("unreadCount", unreadCount);
			latestByOtherUser.put(otherUser.getId(), item);
		}

		return new ArrayList<>(latestByOtherUser.values());
	}

	@Transactional
	public void markAsRead(Long messageId, Long currentUserId) {
		Message message = messageRepository.findById(messageId)
				.orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

		if (message.getReceiver() != null && message.getReceiver().getId().equals(currentUserId)) {
			message.setIsRead(true);
			messageRepository.save(message);
		}
	}

	private ChatMessageDTO toDTO(Message message) {
		return ChatMessageDTO.builder()
				.id(message.getId())
				.senderId(message.getSender() != null ? message.getSender().getId() : null)
				.senderName(message.getSender() != null ? message.getSender().getName() : null)
				.receiverId(message.getReceiver() != null ? message.getReceiver().getId() : null)
				.receiverName(message.getReceiver() != null ? message.getReceiver().getName() : null)
				.classroomId(message.getClassRoom() != null ? message.getClassRoom().getId() : null)
				.content(message.getContent())
				.type(message.getType())
				.isRead(message.getIsRead())
				.createdAt(message.getCreatedAt())
				.build();
	}
}
