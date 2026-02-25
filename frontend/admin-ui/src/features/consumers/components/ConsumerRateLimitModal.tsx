// Модальное окно управления consumer rate limit (Story 12.9, AC8)
import { useEffect } from 'react'
import { Modal, Form, InputNumber, Button, Alert, message } from 'antd'
import {
  useConsumerRateLimit,
  useSetConsumerRateLimit,
  useDeleteConsumerRateLimit,
} from '../hooks/useConsumers'
import type { ConsumerRateLimitRequest } from '../types/consumer.types'

interface ConsumerRateLimitModalProps {
  open: boolean
  consumerId: string
  onClose: () => void
}

/**
 * Модальное окно для управления rate limit consumer.
 *
 * Позволяет создать, обновить или удалить rate limit.
 * Reuse API из Story 12.8.
 */
function ConsumerRateLimitModal({ open, consumerId, onClose }: ConsumerRateLimitModalProps) {
  const [form] = Form.useForm<ConsumerRateLimitRequest>()

  // Загрузка текущего rate limit
  const { data: currentRateLimit, isLoading } = useConsumerRateLimit(consumerId)

  // Мутации
  const setRateLimitMutation = useSetConsumerRateLimit()
  const deleteRateLimitMutation = useDeleteConsumerRateLimit()

  // Инициализация формы текущими значениями
  useEffect(() => {
    if (currentRateLimit) {
      form.setFieldsValue({
        requestsPerSecond: currentRateLimit.requestsPerSecond,
        burstSize: currentRateLimit.burstSize,
      })
    } else {
      form.resetFields()
    }
  }, [currentRateLimit, form])

  // Обработчик отправки формы (upsert)
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()

      setRateLimitMutation.mutate(
        { consumerId, data: values },
        {
          onSuccess: () => {
            message.success('Rate limit updated successfully')
            onClose()
          },
          onError: (error: Error) => {
            message.error(`Failed to update rate limit: ${error.message}`)
          },
        }
      )
    } catch (error) {
      console.error('Validation failed:', error)
    }
  }

  // Обработчик удаления rate limit
  const handleDelete = () => {
    deleteRateLimitMutation.mutate(consumerId, {
      onSuccess: () => {
        message.success('Rate limit removed successfully')
        form.resetFields()
        onClose()
      },
      onError: (error: Error) => {
        message.error(`Failed to remove rate limit: ${error.message}`)
      },
    })
  }

  // Обработчик закрытия
  const handleCancel = () => {
    form.resetFields()
    onClose()
  }

  return (
    <Modal
      title={`Rate Limit для ${consumerId}`}
      open={open}
      onCancel={handleCancel}
      footer={[
        currentRateLimit && (
          <Button key="delete" danger onClick={handleDelete} loading={deleteRateLimitMutation.isPending}>
            Remove Rate Limit
          </Button>
        ),
        <Button key="cancel" onClick={handleCancel}>
          Cancel
        </Button>,
        <Button
          key="submit"
          type="primary"
          onClick={handleSubmit}
          loading={setRateLimitMutation.isPending}
        >
          {currentRateLimit ? 'Update' : 'Set'} Rate Limit
        </Button>,
      ]}
      destroyOnClose
    >
      {isLoading ? (
        <div>Loading...</div>
      ) : (
        <>
          <Alert
            message="Per-consumer Rate Limit"
            description="Rate limit применяется глобально для всех маршрутов этого consumer. Token Bucket алгоритм с Redis."
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />

          <Form form={form} layout="vertical">
            <Form.Item
              name="requestsPerSecond"
              label="Requests per Second"
              rules={[
                { required: true, message: 'Укажите лимит запросов в секунду' },
                { type: 'number', min: 1, max: 10000, message: 'Должно быть от 1 до 10000' },
              ]}
            >
              <InputNumber
                min={1}
                max={10000}
                style={{ width: '100%' }}
                placeholder="Лимит запросов в секунду"
                data-testid="rate-limit-rps-input"
              />
            </Form.Item>

            <Form.Item
              name="burstSize"
              label="Burst Size"
              rules={[
                { required: true, message: 'Укажите burst size' },
                { type: 'number', min: 1, max: 50000, message: 'Должно быть от 1 до 50000' },
              ]}
              tooltip="Максимальное количество токенов в bucket (позволяет кратковременные пики нагрузки)"
            >
              <InputNumber
                min={1}
                max={50000}
                style={{ width: '100%' }}
                placeholder="Максимальный burst"
                data-testid="rate-limit-burst-input"
              />
            </Form.Item>
          </Form>

          {currentRateLimit && (
            <Alert
              message={`Текущий rate limit: ${currentRateLimit.requestsPerSecond} req/s, burst ${currentRateLimit.burstSize}`}
              type="success"
              style={{ marginTop: 16 }}
            />
          )}
        </>
      )}
    </Modal>
  )
}

export default ConsumerRateLimitModal
