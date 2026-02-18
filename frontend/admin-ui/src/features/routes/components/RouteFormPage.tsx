// Страница формы создания/редактирования маршрута (Story 3.5)
import { useEffect, useCallback, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Typography, Button, Spin, Space } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useRoute, useCreateRoute, useUpdateRoute } from '../hooks/useRoutes'
import { RouteForm, type RouteFormRef } from './RouteForm'
import type { CreateRouteRequest, UpdateRouteRequest } from '../types/route.types'

const { Title } = Typography

/**
 * Страница формы создания/редактирования маршрута.
 *
 * Режимы работы:
 * - /routes/new — создание нового маршрута
 * - /routes/:id/edit — редактирование существующего маршрута
 *
 * Поддерживает keyboard shortcut ⌘+Enter (Ctrl+Enter) для сохранения.
 */
export function RouteFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const formRef = useRef<RouteFormRef>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const isEditMode = !!id

  // Загрузка данных маршрута для режима редактирования
  const { data: route, isLoading: isLoadingRoute } = useRoute(id)

  const createMutation = useCreateRoute()
  const updateMutation = useUpdateRoute()

  /**
   * Обработчик отправки формы.
   */
  const handleSubmit = useCallback(
    async (values: CreateRouteRequest | UpdateRouteRequest) => {
      setIsSubmitting(true)
      try {
        if (isEditMode && id) {
          await updateMutation.mutateAsync({ id, request: values as UpdateRouteRequest })
          navigate(`/routes/${id}`)
        } else {
          const newRoute = await createMutation.mutateAsync(values as CreateRouteRequest)
          navigate(`/routes/${newRoute.id}`)
        }
      } finally {
        setIsSubmitting(false)
      }
    },
    [isEditMode, id, createMutation, updateMutation, navigate]
  )

  /**
   * Обработчик отмены/возврата — навигация к списку маршрутов.
   */
  const handleCancel = useCallback(() => {
    navigate('/routes')
  }, [navigate])

  /**
   * Keyboard shortcut ⌘+Enter / Ctrl+Enter для отправки формы.
   */
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
        e.preventDefault()
        formRef.current?.submit()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [])

  // Показываем спиннер пока загружаются данные маршрута в режиме редактирования
  if (isEditMode && isLoadingRoute) {
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: '50px 0' }}>
          <Spin size="large" />
        </div>
      </Card>
    )
  }

  const isPending = createMutation.isPending || updateMutation.isPending || isSubmitting

  return (
    <Card>
      {/* Заголовок страницы */}
      <Space style={{ marginBottom: 24 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={handleCancel} type="text" />
        <Title level={2} style={{ margin: 0 }}>
          {isEditMode ? 'Edit Route' : 'Create Route'}
        </Title>
      </Space>

      {/* Форма маршрута */}
      <RouteForm
        ref={formRef}
        initialValues={route}
        onSubmit={handleSubmit}
        onCancel={handleCancel}
        isSubmitting={isPending}
        mode={isEditMode ? 'edit' : 'create'}
      />
    </Card>
  )
}
