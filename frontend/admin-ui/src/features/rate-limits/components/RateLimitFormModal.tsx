// Модальное окно для создания и редактирования Rate Limit политики (Story 5.4, AC2, AC3, AC4)
import { useEffect } from 'react'
import { Modal, Form, Input, InputNumber } from 'antd'
import { useCreateRateLimit, useUpdateRateLimit } from '../hooks/useRateLimits'
import type { RateLimit, CreateRateLimitRequest, UpdateRateLimitRequest } from '../types/rateLimit.types'

interface RateLimitFormModalProps {
  open: boolean
  /** null — режим создания, RateLimit — режим редактирования */
  rateLimit: RateLimit | null
  onClose: () => void
}

/**
 * Модальное окно для создания и редактирования Rate Limit политики.
 *
 * Поля: name, description, requestsPerSecond, burstSize
 * Валидация: burstSize >= requestsPerSecond (AC2)
 */
function RateLimitFormModal({ open, rateLimit, onClose }: RateLimitFormModalProps) {
  const [form] = Form.useForm()
  const createMutation = useCreateRateLimit()
  const updateMutation = useUpdateRateLimit()

  const isEditMode = rateLimit !== null
  const isLoading = createMutation.isPending || updateMutation.isPending

  // Сброс формы при открытии/смене политики
  useEffect(() => {
    if (open) {
      if (rateLimit) {
        // Режим редактирования — заполняем значениями
        form.setFieldsValue({
          name: rateLimit.name,
          description: rateLimit.description || '',
          requestsPerSecond: rateLimit.requestsPerSecond,
          burstSize: rateLimit.burstSize,
        })
      } else {
        // Режим создания — сбрасываем форму
        form.resetFields()
      }
    }
  }, [open, rateLimit, form])

  // Валидация burstSize >= requestsPerSecond (AC2)
  const validateBurstSize = (_: unknown, value: number) => {
    const requestsPerSecond = form.getFieldValue('requestsPerSecond')
    if (requestsPerSecond && value < requestsPerSecond) {
      return Promise.reject(new Error('Burst size должен быть не меньше requests/sec'))
    }
    return Promise.resolve()
  }

  // Обработчик отправки формы
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()

      if (isEditMode && rateLimit) {
        // Режим редактирования — отправляем только изменённые поля
        const updateData: UpdateRateLimitRequest = {}
        if (values.name !== rateLimit.name) updateData.name = values.name
        if (values.description !== (rateLimit.description || '')) {
          updateData.description = values.description || undefined
        }
        if (values.requestsPerSecond !== rateLimit.requestsPerSecond) {
          updateData.requestsPerSecond = values.requestsPerSecond
        }
        if (values.burstSize !== rateLimit.burstSize) {
          updateData.burstSize = values.burstSize
        }

        await updateMutation.mutateAsync({ id: rateLimit.id, data: updateData })
      } else {
        // Режим создания — отправляем все поля
        const createData: CreateRateLimitRequest = {
          name: values.name,
          description: values.description || undefined,
          requestsPerSecond: values.requestsPerSecond,
          burstSize: values.burstSize,
        }
        await createMutation.mutateAsync(createData)
      }

      onClose()
    } catch {
      // Ошибки валидации обрабатываются автоматически Form
    }
  }

  // Закрытие с очисткой
  const handleCancel = () => {
    form.resetFields()
    createMutation.reset()
    updateMutation.reset()
    onClose()
  }

  return (
    <Modal
      title={isEditMode ? 'Edit Policy' : 'New Policy'}
      open={open}
      onOk={handleSubmit}
      onCancel={handleCancel}
      confirmLoading={isLoading}
      okText={isEditMode ? 'Save' : 'Create'}
      cancelText="Cancel"
      destroyOnHidden
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          requestsPerSecond: 10,
          burstSize: 20,
        }}
      >
        {/* Name — обязательное, уникальное */}
        <Form.Item
          name="name"
          label="Name"
          rules={[
            { required: true, message: 'Name обязателен' },
            { min: 1, message: 'Минимум 1 символ' },
            { max: 100, message: 'Максимум 100 символов' },
          ]}
        >
          <Input placeholder="Введите название политики" />
        </Form.Item>

        {/* Description — опциональное */}
        <Form.Item
          name="description"
          label="Description"
          rules={[{ max: 500, message: 'Максимум 500 символов' }]}
        >
          <Input.TextArea
            placeholder="Описание политики (опционально)"
            rows={2}
          />
        </Form.Item>

        {/* Requests per Second — обязательное, число >= 1 */}
        <Form.Item
          name="requestsPerSecond"
          label="Requests per Second"
          rules={[
            { required: true, message: 'Requests per second обязателен' },
            { type: 'number', min: 1, message: 'Минимум 1' },
          ]}
        >
          <InputNumber
            min={1}
            style={{ width: '100%' }}
            placeholder="Количество запросов в секунду"
          />
        </Form.Item>

        {/* Burst Size — обязательное, число >= requestsPerSecond */}
        <Form.Item
          name="burstSize"
          label="Burst Size"
          dependencies={['requestsPerSecond']}
          rules={[
            { required: true, message: 'Burst size обязателен' },
            { type: 'number', min: 1, message: 'Минимум 1' },
            { validator: validateBurstSize },
          ]}
        >
          <InputNumber
            min={1}
            style={{ width: '100%' }}
            placeholder="Размер burst (максимум токенов)"
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default RateLimitFormModal
