-- V6__add_approval_fields.sql
-- Добавляет поля для approval workflow (Epic 4)
-- Story 4.1: Submit for Approval API
-- Упрощённая версия для тестов gateway-core (без FK на users)

ALTER TABLE routes
ADD COLUMN submitted_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN approved_by UUID,
ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN rejected_by UUID,
ADD COLUMN rejected_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN rejection_reason TEXT;

-- Индекс для запросов pending approvals (Story 4.3)
-- Используем lowercase 'pending' — соответствует формату хранения статусов в БД
CREATE INDEX idx_routes_status_submitted_at ON routes(status, submitted_at)
WHERE status = 'pending';
