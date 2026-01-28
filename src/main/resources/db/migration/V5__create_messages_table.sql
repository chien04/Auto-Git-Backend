-- Create messages table for chat functionality
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    classroom_id BIGINT REFERENCES classrooms(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('PRIVATE', 'CLASS_GROUP')),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_messages_sender ON messages(sender_id);
CREATE INDEX idx_messages_receiver ON messages(receiver_id);
CREATE INDEX idx_messages_classroom ON messages(classroom_id);
CREATE INDEX idx_messages_type ON messages(type);
CREATE INDEX idx_messages_created_at ON messages(created_at);
CREATE INDEX idx_messages_is_read ON messages(is_read);

-- Composite index for private message queries
CREATE INDEX idx_messages_private ON messages(sender_id, receiver_id, type) WHERE type = 'PRIVATE';

-- Composite index for class group message queries
CREATE INDEX idx_messages_class_group ON messages(classroom_id, type) WHERE type = 'CLASS_GROUP';
