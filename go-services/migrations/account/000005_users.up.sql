CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    branch_id UUID,
    last_login TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, username)
);

-- Seed default users matching the in-memory auth users
INSERT INTO users (tenant_id, username, name, email, role, status) VALUES
('admin', 'admin', 'System Administrator', 'admin@athena.com', 'ADMIN', 'ACTIVE'),
('admin', 'manager', 'Branch Manager', 'manager@athena.com', 'MANAGER', 'ACTIVE'),
('admin', 'officer', 'Loan Officer', 'officer@athena.com', 'OFFICER', 'ACTIVE'),
('admin', 'teller@athena.com', 'Senior Teller', 'teller@athena.com', 'TELLER', 'ACTIVE')
ON CONFLICT DO NOTHING;
