-- Performance indexes for detail/compare endpoints

-- Index on center.is_active for filtered lookups (detail endpoint checks isActive)
CREATE INDEX IF NOT EXISTS idx_center_is_active ON center(is_active) WHERE is_active = true;

-- Composite index for the common search pattern: active + establish_type
CREATE INDEX IF NOT EXISTS idx_center_active_establish_type ON center(is_active, establish_type);

-- Add UNIQUE constraints on center_id for all 1:1 child tables to ensure
-- the database enforces the OneToOne relationship and optimizes JOINs.
-- These also serve as unique indexes for the JOIN FETCH queries.
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_building_center_id_unique ON center_building(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_classroom_center_id_unique ON center_classroom(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_teacher_center_id_unique ON center_teacher(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_lesson_day_center_id_unique ON center_lesson_day(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_meal_center_id_unique ON center_meal(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_bus_center_id_unique ON center_bus(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_year_of_work_center_id_unique ON center_year_of_work(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_environment_center_id_unique ON center_environment(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_safety_check_center_id_unique ON center_safety_check(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_mutual_aid_center_id_unique ON center_mutual_aid(center_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_center_after_school_center_id_unique ON center_after_school(center_id);
