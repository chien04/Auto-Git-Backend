-- Create teacher_assignments table
CREATE TABLE teacher_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    local_path VARCHAR(500),
    role VARCHAR(20) DEFAULT 'MAIN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_teacher_assignments_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher_assignments_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT uk_teacher_assignment UNIQUE (teacher_id, assignment_id)
);

CREATE INDEX idx_teacher_assignments_teacher ON teacher_assignments(teacher_id);
CREATE INDEX idx_teacher_assignments_assignment ON teacher_assignments(assignment_id);
