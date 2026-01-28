-- Add score column to student_assignments table
ALTER TABLE student_assignments 
ADD COLUMN score DOUBLE PRECISION;

-- Add comment for clarity
COMMENT ON COLUMN student_assignments.score IS 'Student score on scale of 10 (0-10), converted from GitHub Actions score (0-100)';
