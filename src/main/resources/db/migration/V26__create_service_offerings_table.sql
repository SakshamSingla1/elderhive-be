-- category_id has NO ON DELETE clause (defaults to RESTRICT/NO ACTION) - unlike this
-- project's usual CASCADE-everywhere pattern for ownership relationships, a bookable
-- offering referencing a category is reference data worth protecting: deleting a
-- ServiceCategory that still has offerings under it should fail loudly, not silently
-- orphan/destroy real catalog data. An admin must reassign or remove the offerings first.
CREATE TABLE service_offerings (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category_id BIGINT NOT NULL,
    description TEXT,
    base_price NUMERIC(10, 2),
    duration_minutes INT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_service_offerings_category FOREIGN KEY (category_id)
        REFERENCES service_categories (id)
);

CREATE INDEX idx_service_offerings_category_id ON service_offerings (category_id);
