-- Remove github_token column from student_assignments table
-- GitHub token should always come from GitHubService (env), not stored in database for security

ALTER TABLE student_assignments DROP COLUMN IF EXISTS github_token;
