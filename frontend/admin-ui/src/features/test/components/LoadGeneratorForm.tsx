// Форма настроек генератора нагрузки (Story 8.9, AC2)
import { useState, useMemo } from 'react'
import { Card, Select, InputNumber, Radio, Button, Space, Form, Alert, Spin } from 'antd'
import { PlayCircleOutlined, StopOutlined } from '@ant-design/icons'
import { useRoutes } from '@features/routes'
import type { LoadGeneratorConfig, RouteOption } from '../types/loadGenerator.types'

interface LoadGeneratorFormProps {
  isRunning: boolean
  onStart: (config: LoadGeneratorConfig) => void
  onStop: () => void
}

type DurationMode = 'fixed' | 'until-stopped'

/**
 * Форма настроек генератора нагрузки.
 *
 * Позволяет выбрать:
 * - Target route (опубликованные маршруты)
 * - Requests per second (1-100)
 * - Duration (фиксированное или до остановки)
 */
export function LoadGeneratorForm({ isRunning, onStart, onStop }: LoadGeneratorFormProps) {
  const [selectedRouteId, setSelectedRouteId] = useState<string | null>(null)
  const [requestsPerSecond, setRequestsPerSecond] = useState<number>(10)
  const [durationMode, setDurationMode] = useState<DurationMode>('until-stopped')
  const [durationSeconds, setDurationSeconds] = useState<number>(30)

  // Загружаем только опубликованные маршруты
  const { data: routesData, isLoading: routesLoading } = useRoutes({ status: 'published' })

  // Преобразуем в опции для Select
  const routeOptions: RouteOption[] = useMemo(() => {
    if (!routesData?.items) return []
    return routesData.items.map((route) => ({
      id: route.id,
      path: route.path,
      name: route.name,
    }))
  }, [routesData])

  // Находим выбранный маршрут
  const selectedRoute = useMemo(() => {
    return routeOptions.find((r) => r.id === selectedRouteId)
  }, [routeOptions, selectedRouteId])

  const handleStart = () => {
    if (!selectedRoute) return

    const config: LoadGeneratorConfig = {
      routeId: selectedRoute.id,
      routePath: selectedRoute.path,
      requestsPerSecond,
      durationSeconds: durationMode === 'fixed' ? durationSeconds : null,
    }
    onStart(config)
  }

  const canStart = selectedRouteId && requestsPerSecond > 0

  return (
    <Card title="Load Generator Settings" data-testid="load-generator-form">
      {routesLoading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
          <div style={{ marginTop: 8 }}>Loading routes...</div>
        </div>
      ) : routeOptions.length === 0 ? (
        <Alert
          message="No published routes"
          description="There are no published routes available for load testing. Please publish some routes first."
          type="warning"
          showIcon
          data-testid="no-routes-alert"
        />
      ) : (
        <Form layout="vertical">
          <Form.Item label="Target Route" required>
            <Select
              placeholder="Select a route"
              value={selectedRouteId}
              onChange={setSelectedRouteId}
              disabled={isRunning}
              style={{ width: '100%' }}
              data-testid="route-selector"
              options={routeOptions.map((route) => ({
                value: route.id,
                label: `${route.path} (${route.name})`,
              }))}
            />
          </Form.Item>

          <Form.Item label="Requests per second" required>
            <InputNumber
              min={1}
              max={100}
              value={requestsPerSecond}
              onChange={(value) => setRequestsPerSecond(value ?? 10)}
              disabled={isRunning}
              style={{ width: 120 }}
              data-testid="rps-input"
            />
            <span style={{ marginLeft: 8, color: '#888' }}>(1-100)</span>
          </Form.Item>

          <Form.Item label="Duration">
            <Space direction="vertical">
              <Radio.Group
                value={durationMode}
                onChange={(e) => setDurationMode(e.target.value)}
                disabled={isRunning}
                data-testid="duration-mode"
              >
                <Space direction="vertical">
                  <Radio value="fixed">
                    <Space>
                      Fixed:
                      <InputNumber
                        min={1}
                        max={3600}
                        value={durationSeconds}
                        onChange={(value) => setDurationSeconds(value ?? 30)}
                        disabled={isRunning || durationMode !== 'fixed'}
                        style={{ width: 80 }}
                        data-testid="duration-input"
                      />
                      seconds
                    </Space>
                  </Radio>
                  <Radio value="until-stopped">Until stopped</Radio>
                </Space>
              </Radio.Group>
            </Space>
          </Form.Item>

          <Form.Item>
            {isRunning ? (
              <Button
                type="primary"
                danger
                icon={<StopOutlined />}
                onClick={onStop}
                size="large"
                data-testid="stop-button"
              >
                Stop
              </Button>
            ) : (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={handleStart}
                disabled={!canStart}
                size="large"
                data-testid="start-button"
              >
                Start Load
              </Button>
            )}
          </Form.Item>
        </Form>
      )}
    </Card>
  )
}

export default LoadGeneratorForm
