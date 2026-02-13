-- V1__create_routes.sql
-- ApiGateway Routes Table

CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    path VARCHAR(255) NOT NULL UNIQUE,
    upstream_url VARCHAR(512) NOT NULL,
    methods VARCHAR(20)[] DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft', 'pending', 'published', 'rejected')),
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for filtering by status
CREATE INDEX idx_routes_status ON routes (status);

-- Index for path lookups (gateway routing)
CREATE INDEX idx_routes_path ON routes (path);

-- Comment for documentation
COMMENT ON TABLE routes IS 'API Gateway route configurations';
COMMENT ON COLUMN routes.path IS 'URL path pattern for routing (e.g., /api/orders)';
COMMENT ON COLUMN routes.upstream_url IS 'Target upstream service URL';
COMMENT ON COLUMN routes.methods IS 'Allowed HTTP methods (GET, POST, PUT, DELETE, PATCH)';
COMMENT ON COLUMN routes.status IS 'Route lifecycle status: draft -> pending -> published/rejected';
