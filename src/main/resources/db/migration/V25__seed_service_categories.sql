-- Starter catalog (Prompt 71) - seed DATA, not enum values, so adding a new category from
-- here on is a pure admin CRUD action (POST /api/v1/service-categories), never a code
-- change or migration.
INSERT INTO service_categories (name, description, icon, status, created_at, updated_at)
VALUES
    ('Temple Visit', 'Accompanying an elder to a place of worship.', 'temple', 'ACTIVE', now(), now()),
    ('Hospital Visit', 'Accompanying an elder to a medical appointment or hospital visit.', 'hospital', 'ACTIVE', now(), now()),
    ('Grocery Shopping', 'Shopping for groceries and household essentials on an elder''s behalf.', 'grocery', 'ACTIVE', now(), now()),
    ('Companion Care', 'General companionship and conversation.', 'companion', 'ACTIVE', now(), now()),
    ('Medication Assistance', 'Help with organizing and taking prescribed medications.', 'medication', 'ACTIVE', now(), now()),
    ('Physiotherapy', 'In-home physiotherapy and mobility exercises.', 'physiotherapy', 'ACTIVE', now(), now());
