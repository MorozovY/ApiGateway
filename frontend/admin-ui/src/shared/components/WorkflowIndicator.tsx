// Компонент визуального индикатора workflow маршрута (Story 16.10)
import { Steps, theme } from 'antd'
import {
  EditOutlined,
  SendOutlined,
  SafetyCertificateOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'

/**
 * Шаги workflow маршрута.
 * Отображают жизненный цикл: Создание → Отправка → Согласование → Публикация.
 * Экспортируется для переиспользования в других компонентах.
 */
export const WORKFLOW_STEPS = [
  { title: 'Создание', icon: <EditOutlined /> },
  { title: 'Отправка', icon: <SendOutlined /> },
  { title: 'Согласование', icon: <SafetyCertificateOutlined /> },
  { title: 'Публикация', icon: <CheckCircleOutlined /> },
]

/**
 * Props для компонента WorkflowIndicator.
 */
export interface WorkflowIndicatorProps {
  /** Текущий шаг (0-based index) */
  currentStep: number
  /** Размер компонента */
  size?: 'default' | 'small'
}

/**
 * Компонент визуального индикатора workflow маршрута.
 *
 * Отображает горизонтальный Ant Steps с 4 шагами жизненного цикла маршрута:
 * 1. Создание — новый маршрут
 * 2. Отправка — на согласование
 * 3. Согласование — security review
 * 4. Публикация — активен
 *
 * @param currentStep - номер текущего шага (0-based)
 * @param size - размер компонента (default: 'small')
 */
export function WorkflowIndicator({
  currentStep,
  size = 'small',
}: WorkflowIndicatorProps) {
  const { token } = theme.useToken()

  return (
    <div
      style={{
        padding: '12px 16px',
        marginBottom: 16,
        backgroundColor: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadius,
      }}
      data-testid="workflow-indicator"
    >
      <Steps current={currentStep} size={size} items={WORKFLOW_STEPS} />
    </div>
  )
}
