-- V6__add_approval_fields.sql
-- Добавляет поля для approval workflow (Epic 4)
-- Story 4.1: Submit for Approval API

ALTER TABLE routes
ADD COLUMN submitted_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN approved_by UUID REFERENCES users(id),
ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN rejected_by UUID REFERENCES users(id),
ADD COLUMN rejected_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN rejection_reason TEXT;

-- Индекс для запросов pending approvals (Story 4.3)
-- Используем lowercase 'pending' — соответствует формату хранения статусов в БД
CREATE INDEX idx_routes_status_submitted_at ON routes(status, submitted_at)
WHERE status = 'pending';

COMMENT ON COLUMN routes.submitted_at IS 'Время отправки на согласование';
COMMENT ON COLUMN routes.approved_by IS 'ID пользователя, одобрившего маршрут';
COMMENT ON COLUMN routes.approved_at IS 'Время одобрения';
COMMENT ON COLUMN routes.rejected_by IS 'ID пользователя, отклонившего маршрут';
COMMENT ON COLUMN routes.rejected_at IS 'Время отклонения';
COMMENT ON COLUMN routes.rejection_reason IS 'Причина отклонения';
