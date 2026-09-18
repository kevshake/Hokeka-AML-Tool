CREATE TABLE multi_asset_customer_relationships (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id) ON DELETE CASCADE,
    related_customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id) ON DELETE CASCADE,
    relationship_type VARCHAR(32) NOT NULL,
    ownership_percentage NUMERIC(7,4),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_relationship_not_self CHECK (customer_id <> related_customer_id),
    CONSTRAINT chk_ownership_percentage CHECK (ownership_percentage IS NULL OR
        (ownership_percentage >= 0 AND ownership_percentage <= 100)),
    CONSTRAINT uq_multi_asset_customer_relationship UNIQUE
        (psp_id, customer_id, related_customer_id, relationship_type)
);

CREATE INDEX idx_multi_asset_relationship_customer
    ON multi_asset_customer_relationships(psp_id, customer_id);
CREATE INDEX idx_multi_asset_relationship_related
    ON multi_asset_customer_relationships(psp_id, related_customer_id);

