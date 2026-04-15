-- Migration script to change from class-based repos to assignment-based repos
-- Date: 2024-12-30

-- Step 1: Create new assignments table
CREATE TABLE IF NOT EXISTS assignments (
    id BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT NOT NULL REFERENCES classrooms(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    assignment_code VARCHAR(8) NOT NULL UNIQUE,
    repo_url VARCHAR(500) NOT NULL,
    repo_name VARCHAR(255) NOT NULL,
    github_repo_id BIGINT,
    is_active BOOLEAN DEFAULT true,
    deadline TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Step 2: Create student_assignments junction table
CREATE TABLE IF NOT EXISTS student_assignments (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id),
    assignment_id BIGINT NOT NULL REFERENCES assignments(id),
    branch_name VARCHAR(255) NOT NULL,
    github_token VARCHAR(500),
    last_commit_at TIMESTAMP,
    commit_count INTEGER DEFAULT 0,
    local_path VARCHAR(500),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(student_id, assignment_id)
);

-- Step 3: Backup existing student data (if needed for rollback)
CREATE TABLE IF NOT EXISTS students_backup AS 
SELECT * FROM students;

-- Step 4: Remove columns from students table that moved to student_assignments
-- Note: These will be moved to student_assignments table
-- branch_name, github_token, last_commit_at, commit_count, local_path
ALTER TABLE students DROP COLUMN IF EXISTS branch_name;
ALTER TABLE students DROP COLUMN IF EXISTS github_token;
ALTER TABLE students DROP COLUMN IF EXISTS last_commit_at;
ALTER TABLE students DROP COLUMN IF EXISTS commit_count;
ALTER TABLE students DROP COLUMN IF EXISTS local_path;

-- Step 5: Remove repo-related columns from classrooms table
-- Note: Repos now belong to assignments, not classrooms
ALTER TABLE classrooms DROP COLUMN IF EXISTS repo_url;
ALTER TABLE classrooms DROP COLUMN IF EXISTS repo_name;
ALTER TABLE classrooms DROP COLUMN IF EXISTS github_repo_id;
ALTER TABLE classrooms DROP COLUMN IF EXISTS local_path;
ALTER TABLE classrooms DROP COLUMN IF EXISTS deadline;

-- Step 6: Add indexes for performance
CREATE INDEX idx_assignments_classroom ON assignments(classroom_id);
CREATE INDEX idx_assignments_code ON assignments(assignment_code);
CREATE INDEX idx_student_assignments_student ON student_assignments(student_id);
CREATE INDEX idx_student_assignments_assignment ON student_assignments(assignment_id);

-- Step 6.1: Create comments table for inline code feedback
CREATE TABLE IF NOT EXISTS comments (
    id BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT NOT NULL REFERENCES assignments(id),
    student_id BIGINT NOT NULL REFERENCES students(id),
    author_id BIGINT NOT NULL REFERENCES users(id),
    target_branch VARCHAR(255) NOT NULL,
    student_file_path VARCHAR(1000) NOT NULL,
    teacher_file_path VARCHAR(1000),
    start_line INTEGER NOT NULL,
    start_column INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    end_column INTEGER NOT NULL,
    selected_text TEXT,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comments_assignment_student_file
    ON comments(assignment_id, student_id, student_file_path);
CREATE INDEX IF NOT EXISTS idx_comments_assignment_branch
    ON comments(assignment_id, target_branch);

-- Step 7: Add indexes on students table
CREATE INDEX IF NOT EXISTS idx_students_classroom ON students(classroom_id);
CREATE INDEX IF NOT EXISTS idx_students_user ON students(user_id);

-- Notes for manual migration:
-- 1. If you have existing classes with repositories, you'll need to:
--    a) Create assignments for each existing class
--    b) Move the repo info from classrooms to assignments
--    c) Move student branch info from students to student_assignments
-- 2. This script assumes a fresh start or test environment
-- 3. For production, create a data migration script to preserve existing data

COMMIT;
