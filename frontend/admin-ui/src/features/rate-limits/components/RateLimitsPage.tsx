// Страница управления Rate Limit политиками (Story 5.4, AC1-AC8)
import { useState } from 'react'
import { Button, Typography, App } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import RateLimitsTable from './RateLimitsTable'
import RateLimitFormModal from './RateLimitFormModal'
import RateLimitRoutesModal from './RateLimitRoutesModal'
import { useDeleteRateLimit } from '../hooks/useRateLimits'
import { useAuth } from '@features/auth'
import type { RateLimit } from '../types/rateLimit.types'

const { Title } = Typography

/**
 * Страница управления Rate Limit политиками.
 *
 * - Таблица со списком политик
 * - Создание/редактирование через модальное окно
 * - Просмотр использующих маршрутов (AC7)
 * - Удаление с проверкой usageCount (AC5, AC6)
 * - Кнопки Edit/Delete/New Policy только для admin (AC8)
 */
function RateLimitsPage() {
  const { user } = useAuth()
  const { message } = App.useApp()
  const isAdmin = user?.role === 'admin'

  // Состояние модального окна формы
  const [isFormModalOpen, setIsFormModalOpen] = useState(false)
  const [editingRateLimit, setEditingRateLimit] = useState<RateLimit | null>(null)

  // Состояние модального окна маршрутов
  const [isRoutesModalOpen, setIsRoutesModalOpen] = useState(false)
  const [selectedRateLimit, setSelectedRateLimit] = useState<RateLimit | null>(null)

  // Мутация удаления
  const deleteMutation = useDeleteRateLimit()

  // Открытие модального окна для создания
  const handleAdd = () => {
    setEditingRateLimit(null)
    setIsFormModalOpen(true)
  }

  // Открытие модального окна для редактирования
  const handleEdit = (rateLimit: RateLimit) => {
    setEditingRateLimit(rateLimit)
    setIsFormModalOpen(true)
  }

  // Закрытие модального окна формы
  const handleFormModalClose = () => {
    setIsFormModalOpen(false)
    setEditingRateLimit(null)
  }

  // Обработчик удаления (AC5, AC6)
  const handleDelete = (rateLimit: RateLimit) => {
    // Проверяем usageCount на клиенте (дополнительная защита, основная — на сервере)
    if (rateLimit.usageCount > 0) {
      message.error(`Cannot delete: policy is used by ${rateLimit.usageCount} routes`)
      return
    }
    deleteMutation.mutate(rateLimit.id)
  }

  // Открытие модального окна маршрутов (AC7)
  const handleViewRoutes = (rateLimit: RateLimit) => {
    setSelectedRateLimit(rateLimit)
    setIsRoutesModalOpen(true)
  }

  // Закрытие модального окна маршрутов
  const handleRoutesModalClose = () => {
    setIsRoutesModalOpen(false)
    setSelectedRateLimit(null)
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>
          Rate Limit Policies
        </Title>
        {/* Кнопка "New Policy" только для admin (AC8) */}
        {isAdmin && (
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            New Policy
          </Button>
        )}
      </div>

      <RateLimitsTable
        onEdit={isAdmin ? handleEdit : undefined}
        onDelete={isAdmin ? handleDelete : undefined}
        onViewRoutes={handleViewRoutes}
        isDeleting={deleteMutation.isPending}
      />

      <RateLimitFormModal
        open={isFormModalOpen}
        rateLimit={editingRateLimit}
        onClose={handleFormModalClose}
      />

      <RateLimitRoutesModal
        open={isRoutesModalOpen}
        rateLimit={selectedRateLimit}
        onClose={handleRoutesModalClose}
      />
    </div>
  )
}

export default RateLimitsPage
