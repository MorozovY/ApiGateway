// Страница деталей маршрута (Story 3.6)
import { useParams, useNavigate } from 'react-router-dom'
import { Spin, Result, Button } from 'antd'
import { useRoute } from '../hooks/useRoutes'
import { RouteDetailsCard } from './RouteDetailsCard'

/**
 * Страница просмотра деталей маршрута.
 *
 * Отображает полную информацию о маршруте в card layout.
 * Показывает 404 страницу если маршрут не найден.
 */
export function RouteDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { data: route, isLoading, error } = useRoute(id)

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

  return <RouteDetailsCard route={route} />
}
