CREATE EXTENSION IF NOT EXISTS postgis;

-- Performance indexes (safe to run on every startup with IF NOT EXISTS)
-- Partial index on is_active for filtered lookups
CREATE INDEX IF NOT EXISTS idx_center_is_active ON center(is_active) WHERE is_active = true;
-- Composite index for active + establish_type search pattern
CREATE INDEX IF NOT EXISTS idx_center_active_establish_type ON center(is_active, establish_type);
-- Unique constraints on center_id for 1:1 child tables (optimizes JOIN FETCH)
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

-- Trigram indexes for LIKE/ILIKE text search on name and address
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_center_name_trgm ON center USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_center_address_trgm ON center USING GIN (address gin_trgm_ops);

-- Index for default ORDER BY updated_at DESC
CREATE INDEX IF NOT EXISTS idx_center_updated_at_desc ON center(updated_at DESC);

-- Composite index for active centers sorted by updated_at (most common query pattern)
CREATE INDEX IF NOT EXISTS idx_center_active_updated ON center(is_active, updated_at DESC) WHERE is_active = true;

-- Center review indexes
CREATE INDEX IF NOT EXISTS idx_center_review_center_id ON center_review(center_id);
CREATE INDEX IF NOT EXISTS idx_center_review_center_postdate ON center_review(center_id, post_date DESC NULLS LAST);

-- User review indexes
CREATE INDEX IF NOT EXISTS idx_user_review_center_id ON user_review(center_id);
CREATE INDEX IF NOT EXISTS idx_user_review_device_id ON user_review(device_id);
CREATE INDEX IF NOT EXISTS idx_user_review_center_created ON user_review(center_id, created_at DESC);
