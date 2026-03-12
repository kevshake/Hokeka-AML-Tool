-- ===================================================================================
-- V14: Create Roles and Platform Users Tables
-- These tables are required by V99 and subsequent migrations
-- Must run BEFORE V99__Dummy_Credentials.sql
-- ===================================================================================

-- Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    psp_id BIGINT REFERENCES psps(psp_id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_name_psp UNIQUE (name, psp_id)
);

CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);
CREATE INDEX IF NOT EXISTS idx_roles_psp ON roles(psp_id);

COMMENT ON TABLE roles IS 'User roles for access control, can be global (psp_id=NULL) or PSP-specific';

-- Platform Users Table (legacy name for user accounts)
CREATE TABLE IF NOT EXISTS platform_users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role_id INTEGER REFERENCES roles(id),
    psp_id BIGINT REFERENCES psps(psp_id),
    enabled BOOLEAN DEFAULT TRUE,
    email_verified BOOLEAN DEFAULT FALSE,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_platform_users_username ON platform_users(username);
CREATE INDEX IF NOT EXISTS idx_platform_users_email ON platform_users(email);
CREATE INDEX IF NOT EXISTS idx_platform_users_psp ON platform_users(psp_id);
CREATE INDEX IF NOT EXISTS idx_platform_users_role ON platform_users(role_id);
CREATE INDEX IF NOT EXISTS idx_platform_users_enabled ON platform_users(enabled);

COMMENT ON TABLE platform_users IS 'User accounts for platform access (legacy name for backward compatibility)';

-- Role Permissions Table (if not already created by V17)
CREATE TABLE IF NOT EXISTS role_permissions (
    id SERIAL PRIMARY KEY,
    user_role VARCHAR(50) NOT NULL,
    permission VARCHAR(100) NOT NULL,
    granted_by VARCHAR(100),
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    CONSTRAINT uk_role_permission UNIQUE (user_role, permission)
);

CREATE INDEX IF NOT EXISTS idx_role_perm_lookup ON role_permissions(user_role, permission);

COMMENT ON TABLE role_permissions IS 'Permissions assigned to roles';

-- User Roles Junction Table (for many-to-many if needed)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id INTEGER REFERENCES platform_users(id) ON DELETE CASCADE,
    role_id INTEGER REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by INTEGER REFERENCES platform_users(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id);

-- Trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER roles_updated_at BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER platform_users_updated_at BEFORE UPDATE ON platform_users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===================================================================================
-- END OF MIGRATION
-- ===================================================================================
