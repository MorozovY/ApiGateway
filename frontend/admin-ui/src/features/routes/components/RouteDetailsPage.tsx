// Страница деталей маршрута (Story 3.6, расширена в Story 7.6 для Tabs с историей)
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { Spin, Result, Button, Tabs, Card } from 'antd'
import { FileTextOutlined, HistoryOutlined } from '@ant-design/icons'
import { useRoute } from '../hooks/useRoutes'
import { RouteDetailsCard } from './RouteDetailsCard'
import { RouteHistoryTimeline } from '@features/audit'

/**
 * Страница просмотра деталей маршрута.
 *
 * Отображает полную информацию о маршруте в card layout с tabs:
 * - Tab 1: "Детали" — существующий RouteDetailsCard
 * - Tab 2: "История" — RouteHistoryTimeline с историей изменений
 *
 * Tabs key синхронизируется с URL hash (#details, #history).
 * History tab виден ВСЕМ ролям (readonly информация).
 */
export function RouteDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const { data: route, isLoading, error } = useRoute(id)

  // Синхронизация tab с URL hash (AC1)
  const activeTab = location.hash === '#history' ? 'history' : 'details'

  // Обработчик смены tab — обновляем URL hash
  const handleTabChange = (key: string) => {
    navigate(`${location.pathname}#${key}`, { replace: true })
  }

  // Состояние загрузки
  if (isLoading) {
    return (
      <div className="page-loading-spinner">
        <Spin size="large" />
      </div>
    )
  }

  // 404 для несуществующего маршрута
  if (error || !route) {
    return (
      <Result
        status="404"
        title="Маршрут не найден"
        subTitle="Маршрут с указанным ID не существует"
        extra={
          <Button type="primary" onClick={() => navigate('/routes')}>
            Вернуться к списку
          </Button>
        }
      />
    )
  }

  return (
    <Card>
      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        items={[
          {
            key: 'details',
            label: (
              <span>
                <FileTextOutlined />
                Детали
              </span>
            ),
            children: <RouteDetailsCard route={route} />,
          },
          {
            key: 'history',
            label: (
              <span>
                <HistoryOutlined />
                История
              </span>
            ),
            children: <RouteHistoryTimeline routeId={route.id} />,
          },
        ]}
      />
    </Card>
  )
}
