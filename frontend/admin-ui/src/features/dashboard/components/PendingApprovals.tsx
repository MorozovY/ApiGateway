/**
 * Компонент уведомления о pending approvals для Security/Admin (Story 16.2, AC2)
 *
 * Отображает Alert с количеством маршрутов, ожидающих согласования.
 * Кнопка ведёт на страницу /approvals.
 *
 * Доступен только для ролей SECURITY и ADMIN.
 */
import { Alert, Button } from 'antd'
import { SafetyCertificateOutlined, ArrowRightOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useDashboardSummary } from '../hooks/useDashboard'
import { useAuth } from '@features/auth'

/**
 * Компонент PendingApprovals — уведомление о маршрутах на согласовании.
 *
 * Отображается только для SECURITY и ADMIN.
 */
export function PendingApprovals() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data, isLoading } = useDashboardSummary()

  // Показывать только для Security и Admin
  if (!user || (user.role !== 'security' && user.role !== 'admin')) {
    return null
  }

  // Если данные ещё не загружены
  if (isLoading) {
    return null
  }

  const pendingCount = data?.pendingApprovalsCount ?? 0

  // Не показываем если нет pending маршрутов
  if (pendingCount === 0) {
    return null
  }

  // Склонение слова "маршрут"
  const getRouteWord = (count: number): string => {
    const lastDigit = count % 10
    const lastTwoDigits = count % 100

    if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
      return 'маршрутов'
    }

    if (lastDigit === 1) {
      return 'маршрут'
    }

    if (lastDigit >= 2 && lastDigit <= 4) {
      return 'маршрута'
    }

    return 'маршрутов'
  }

  return (
    <Alert
      type="info"
      icon={<SafetyCertificateOutlined />}
      message={`${pendingCount} ${getRouteWord(pendingCount)} ожидает согласования`}
      action={
        <Button
          type="primary"
          size="small"
          onClick={() => navigate('/approvals')}
          icon={<ArrowRightOutlined />}
        >
          Перейти
        </Button>
      }
      style={{ marginBottom: 24 }}
      showIcon
    />
  )
}
