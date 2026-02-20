// Компонент Timeline для отображения истории изменений маршрута (Story 7.6, AC1, AC2)
import { Timeline, Card, Tag, Collapse, Empty, Alert, Skeleton } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import 'dayjs/locale/ru'
import { useRouteHistory } from '../hooks/useRouteHistory'
import { ChangesViewer } from './ChangesViewer'
import { AUDIT_ACTION_COLORS, AUDIT_ACTION_LABELS } from '../config/auditConfig'

// Настройка локали
dayjs.locale('ru')

interface RouteHistoryTimelineProps {
  /** ID маршрута для загрузки истории */
  routeId: string
}

/**
 * Компонент для отображения истории изменений маршрута в виде Timeline.
 *
 * Особенности:
 * - Вертикальный timeline с dots для каждого события
 * - Action badges с цветами из auditConfig
 * - Expandable items для просмотра изменений (Collapse)
 * - Интеграция ChangesViewer для diff view
 * - Most recent события наверху
 * - Loading skeleton во время загрузки
 * - Empty state для маршрутов без истории
 */
export function RouteHistoryTimeline({ routeId }: RouteHistoryTimelineProps) {
  const { data, isLoading, error } = useRouteHistory(routeId)

  // Состояние загрузки — показываем skeleton (AC1)
  if (isLoading) {
    return (
      <div style={{ padding: '16px 0' }}>
        <Skeleton active paragraph={{ rows: 4 }} />
        <Skeleton active paragraph={{ rows: 4 }} style={{ marginTop: 24 }} />
      </div>
    )
  }

  // Ошибка загрузки
  if (error) {
    return (
      <Alert
        type="error"
        message="Ошибка загрузки истории"
        description={error instanceof Error ? error.message : 'Не удалось загрузить историю изменений'}
        showIcon
      />
    )
  }

  // Пустое состояние — нет истории (AC9)
  if (!data?.history?.length) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="История изменений отсутствует"
      />
    )
  }

  return (
    <Timeline
      mode="left"
      items={data.history.map((entry, idx) => ({
        key: idx,
        color: AUDIT_ACTION_COLORS[entry.action] || 'gray',
        dot: <ClockCircleOutlined style={{ fontSize: 16 }} />,
        label: (
          <span style={{ color: '#8c8c8c', fontSize: 12 }}>
            {dayjs(entry.timestamp).format('DD MMM YYYY, HH:mm')}
          </span>
        ),
        children: (
          <Card
            size="small"
            style={{ marginBottom: 8 }}
            bodyStyle={{ padding: '12px 16px' }}
          >
            {/* Заголовок события: action badge + username */}
            <div style={{ marginBottom: entry.changes ? 8 : 0 }}>
              <Tag color={AUDIT_ACTION_COLORS[entry.action]}>
                {AUDIT_ACTION_LABELS[entry.action] || entry.action}
              </Tag>
              <span style={{ marginLeft: 8, color: '#595959' }}>
                {entry.user.username}
              </span>
            </div>

            {/* Expandable детали изменений (AC2) */}
            {entry.changes && (
              <Collapse
                ghost
                size="small"
                items={[
                  {
                    key: 'changes',
                    label: 'Показать изменения',
                    children: (
                      <ChangesViewer
                        before={entry.changes.before}
                        after={entry.changes.after}
                        action={entry.action}
                      />
                    ),
                  },
                ]}
              />
            )}
          </Card>
        ),
      }))}
    />
  )
}
