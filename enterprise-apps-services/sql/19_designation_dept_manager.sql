-- Add manager_id to designation_departments table
-- This allows the system to automatically assign the department manager
-- when an incident report targets a specific designation department.

ALTER TABLE designation_departments
ADD COLUMN IF NOT EXISTS manager_id BIGINT NULL;

ALTER TABLE designation_departments
ADD CONSTRAINT fk_designation_dept_manager
FOREIGN KEY (manager_id) REFERENCES users(id)
ON DELETE SET NULL;

-- Example: Update manager_id for existing departments
-- UPDATE designation_departments SET manager_id = <user_id> WHERE id = <dept_id>;
