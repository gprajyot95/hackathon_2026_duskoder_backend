-- PostgreSQL Sample Stored Function Script
-- Run this script on your PostgreSQL database to create sample data and stored function.

-- 1. Create a sample table
CREATE TABLE IF NOT EXISTS system_config (
    id SERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Insert sample data
INSERT INTO system_config (config_key, config_value)
VALUES 
    ('app_name', 'Spring Boot Redis Service'),
    ('version', '1.0.0'),
    ('environment', 'production')
ON CONFLICT (config_key) DO UPDATE 
SET config_value = EXCLUDED.config_value, updated_at = CURRENT_TIMESTAMP;

-- 3. Create stored function returning JSON payload
CREATE OR REPLACE FUNCTION get_stored_data()
RETURNS json AS $$
DECLARE
    result json;
BEGIN
    SELECT json_build_object(
        'status', 'success',
        'generated_at', CURRENT_TIMESTAMP,
        'configs', (
            SELECT json_agg(json_build_object('key', config_key, 'value', config_value))
            FROM system_config
        )
    ) INTO result;
    
    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- Test function execution in PostgreSQL console:
-- SELECT get_stored_data();
