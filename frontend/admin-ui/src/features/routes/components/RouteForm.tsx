// Компонент формы маршрута (Story 3.5, расширен в Story 5.5, 12.7)
import { forwardRef, useImperativeHandle, useCallback, useRef, useState, useEffect } from 'react'
import { Form, Input, Select, Button, Space, Switch, Tooltip } from 'antd'
import { LockOutlined, UnlockOutlined, QuestionCircleOutlined } from '@ant-design/icons'
import type { Route, CreateRouteRequest, UpdateRouteRequest } from '../types/route.types'
import { checkPathExists } from '../api/routesApi'
import { useRateLimits } from '@features/rate-limits'

/**
 * Доступные HTTP методы для маршрута.
 */
const HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

/**
 * Интерфейс пропсов формы маршрута.
 */
interface RouteFormProps {
  /** Начальные значения для режима редактирования */
  initialValues?: Route
  /** Callback при отправке формы */
  onSubmit: (values: CreateRouteRequest | UpdateRouteRequest) => Promise<void>
  /** Callback при отмене */
  onCancel: () => void
  /** Флаг состояния загрузки */
  isSubmitting: boolean
  /** Режим формы: создание или редактирование */
  mode: 'create' | 'edit'
}

/**
 * Интерфейс для внешнего управления формой.
 */
export interface RouteFormRef {
  /** Программно отправить форму */
  submit: () => void
}

/**
 * Задержка debounce для проверки уникальности path (ms).
 */
const PATH_CHECK_DEBOUNCE_MS = 500

/**
 * Форма для создания и редактирования маршрута.
 *
 * Поддерживает:
 * - Inline валидацию полей
 * - Debounced проверку уникальности path (500ms)
 * - Loading state для кнопки сохранения
 */
export const RouteForm = forwardRef<RouteFormRef, RouteFormProps>(function RouteForm(
  { initialValues, onSubmit, onCancel, isSubmitting, mode },
  ref
) {
  const [form] = Form.useForm()
  const [pathError, setPathError] = useState<string | null>(null)
  const [isCheckingPath, setIsCheckingPath] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const currentPathRef = useRef<string | null>(null)

  // Загрузка списка Rate Limit политик (Story 5.5)
  const { data: rateLimitsData, isLoading: rateLimitsLoading } = useRateLimits()

  // Сохраняем оригинальный path для режима редактирования
  // (не проверяем уникальность для собственного path)
  const originalPath = initialValues?.path

  // Инициализация формы при загрузке данных
  useEffect(() => {
    if (initialValues) {
      // Убираем начальный "/" из path для отображения в input
      // (он добавляется как addonBefore)
      const pathWithoutSlash = initialValues.path.startsWith('/')
        ? initialValues.path.slice(1)
        : initialValues.path

      form.setFieldsValue({
        path: pathWithoutSlash,
        upstreamUrl: initialValues.upstreamUrl,
        methods: initialValues.methods,
        description: initialValues.description || '',
        rateLimitId: initialValues.rateLimitId || null,
        authRequired: initialValues.authRequired ?? true,
        allowedConsumers: initialValues.allowedConsumers || [],
      })
    }
  }, [initialValues, form])

  /**
   * Проверка уникальности path с debounce.
   */
  const validatePathUniqueness = useCallback(
    async (path: string) => {
      // Очищаем предыдущий таймер
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
      }

      // Формируем полный path с префиксом
      const fullPath = path.startsWith('/') ? path : `/${path}`
      currentPathRef.current = fullPath

      // Не проверяем уникальность для собственного path в режиме редактирования
      if (mode === 'edit' && fullPath === originalPath) {
        setPathError(null)
        return
      }

      // Пустой path не проверяем
      if (!path.trim()) {
        setPathError(null)
        return
      }

      debounceRef.current = setTimeout(async () => {
        // Проверяем, что path не изменился за время debounce
        if (currentPathRef.current !== fullPath) {
          return
        }

        setIsCheckingPath(true)
        try {
          const exists = await checkPathExists(fullPath)
          // Проверяем актуальность результата
          if (currentPathRef.current === fullPath) {
            if (exists) {
              setPathError('Path already exists')
            } else {
              setPathError(null)
            }
          }
        } catch {
          // Игнорируем ошибки проверки — валидация на сервере поймает
        } finally {
          setIsCheckingPath(false)
        }
      }, PATH_CHECK_DEBOUNCE_MS)
    },
    [mode, originalPath]
  )

  /**
   * Обработчик изменения поля path.
   */
  const handlePathChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const value = e.target.value
      validatePathUniqueness(value)
    },
    [validatePathUniqueness]
  )

  /**
   * Обработчик отправки формы.
   */
  const handleFinish = useCallback(
    async (values: {
      path: string
      upstreamUrl: string
      methods: string[]
      description?: string
      rateLimitId?: string | null
      authRequired?: boolean
      allowedConsumers?: string[]
    }) => {
      // Блокируем отправку если есть ошибка уникальности path
      if (pathError) {
        return
      }

      // Добавляем "/" к path если его нет
      const fullPath = values.path.startsWith('/') ? values.path : `/${values.path}`

      // Преобразуем пустую строку (опция "None") в null
      const rateLimitId = values.rateLimitId && values.rateLimitId !== '' ? values.rateLimitId : null

      // Преобразуем пустой массив allowedConsumers в null (Story 12.7)
      const allowedConsumers = values.allowedConsumers && values.allowedConsumers.length > 0
        ? values.allowedConsumers
        : null

      const request: CreateRouteRequest | UpdateRouteRequest = {
        path: fullPath,
        upstreamUrl: values.upstreamUrl,
        methods: values.methods,
        description: values.description || undefined,
        rateLimitId,
        authRequired: values.authRequired ?? true,
        allowedConsumers,
      }

      await onSubmit(request)
    },
    [onSubmit, pathError]
  )

  // Экспортируем метод submit для внешнего управления
  useImperativeHandle(ref, () => ({
    submit: () => {
      form.submit()
    },
  }))

  // Очистка таймера при размонтировании
  useEffect(() => {
    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
      }
    }
  }, [])

  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={handleFinish}
      style={{ maxWidth: 600 }}
    >
      {/* Поле Path */}
      <Form.Item
        name="path"
        label="Path"
        validateStatus={pathError ? 'error' : isCheckingPath ? 'validating' : undefined}
        help={pathError || undefined}
        rules={[
          { required: true, message: 'Path обязателен' },
          {
            pattern: /^[a-zA-Z0-9\-_/]+$/,
            message: 'Path может содержать только буквы, цифры, дефисы и подчёркивания',
          },
        ]}
        validateTrigger={['onChange', 'onBlur']}
      >
        <Input
          placeholder="api/service"
          addonBefore="/"
          onChange={handlePathChange}
        />
      </Form.Item>

      {/* Поле Upstream URL */}
      <Form.Item
        name="upstreamUrl"
        label="Upstream URL"
        rules={[
          { required: true, message: 'Upstream URL обязателен' },
          { type: 'url', message: 'Некорректный формат URL' },
        ]}
        validateTrigger={['onChange', 'onBlur']}
      >
        <Input placeholder="http://service:8080" />
      </Form.Item>

      {/* Поле Methods */}
      <Form.Item
        name="methods"
        label="HTTP Methods"
        rules={[{ required: true, message: 'Выберите минимум один метод' }]}
      >
        <Select
          mode="multiple"
          placeholder="Выберите методы"
          data-testid="methods-select"
          options={HTTP_METHODS.map((m) => ({ value: m, label: m }))}
        />
      </Form.Item>

      {/* Поле выбора политики Rate Limit (Story 5.5) */}
      <Form.Item
        name="rateLimitId"
        label="Rate Limit Policy"
        data-testid="rate-limit-form-item"
      >
        <Select
          placeholder="Выберите политику (опционально)"
          allowClear
          loading={rateLimitsLoading}
          data-testid="rate-limit-select"
          options={[
            { value: '', label: 'None' },
            ...(rateLimitsData?.items || []).map((policy) => ({
              value: policy.id,
              label: `${policy.name} (${policy.requestsPerSecond}/sec)`,
            })),
          ]}
        />
      </Form.Item>

      {/* Authentication Required Toggle (Story 12.7) */}
      <Form.Item
        name="authRequired"
        label={
          <Space>
            <span>Authentication Required</span>
            <Tooltip title="Если включено, маршрут требует валидный JWT токен. Public маршруты доступны без аутентификации.">
              <QuestionCircleOutlined style={{ color: '#999' }} />
            </Tooltip>
          </Space>
        }
        valuePropName="checked"
        initialValue={true}
        data-testid="auth-required-form-item"
      >
        <Switch
          checkedChildren={<><LockOutlined /> Protected</>}
          unCheckedChildren={<><UnlockOutlined /> Public</>}
          data-testid="auth-required-switch"
        />
      </Form.Item>

      {/* Allowed Consumers Multi-select (Story 12.7) */}
      <Form.Item
        name="allowedConsumers"
        label={
          <Space>
            <span>Allowed Consumers</span>
            <Tooltip title="Оставьте пустым для доступа всем consumers. Укажите client_id для ограничения доступа (whitelist).">
              <QuestionCircleOutlined style={{ color: '#999' }} />
            </Tooltip>
          </Space>
        }
        data-testid="allowed-consumers-form-item"
      >
        <Select
          mode="tags"
          placeholder="Введите consumer IDs (опционально)"
          tokenSeparators={[',', ' ']}
          allowClear
          data-testid="allowed-consumers-select"
        />
      </Form.Item>

      {/* Поле Description */}
      <Form.Item name="description" label="Description">
        <Input.TextArea rows={3} placeholder="Описание маршрута (опционально)" />
      </Form.Item>

      {/* Кнопки действий */}
      <Form.Item>
        <Space>
          <Button type="primary" htmlType="submit" loading={isSubmitting}>
            Save as Draft
          </Button>
          <Button onClick={onCancel}>Cancel</Button>
        </Space>
      </Form.Item>
    </Form>
  )
})
