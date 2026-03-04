// Компонент информационного блока для страниц (Story 15.4)
import { Collapse, Space, Typography, theme } from 'antd'
import { InfoCircleOutlined, DownOutlined, UpOutlined } from '@ant-design/icons'
import { useCallback, useState, useEffect } from 'react'
import type { PageKey } from '../config/pageDescriptions'

const { Text } = Typography

/**
 * Интерфейс описания страницы.
 */
export interface PageDescription {
  /** Заголовок вкладки */
  title: string
  /** Краткое описание назначения */
  description: string
  /** Список ключевых возможностей */
  features: string[]
}

/**
 * Props для компонента PageInfoBlock.
 */
export interface PageInfoBlockProps extends PageDescription {
  /** Уникальный ключ страницы для сохранения состояния в localStorage */
  pageKey: PageKey
}

/** Префикс ключа в localStorage */
const STORAGE_KEY_PREFIX = 'pageInfoBlock_'

/**
 * Получает состояние collapse из localStorage.
 * По умолчанию блок развёрнут для новых пользователей.
 */
function getStoredState(pageKey: PageKey): boolean {
  try {
    const stored = localStorage.getItem(`${STORAGE_KEY_PREFIX}${pageKey}`)
    // Если значение не сохранено — блок развёрнут (для новых пользователей)
    if (stored === null) return true
    return stored === 'expanded'
  } catch {
    // localStorage может быть недоступен (SSR, privacy mode)
    return true
  }
}

/**
 * Сохраняет состояние collapse в localStorage.
 */
function setStoredState(pageKey: PageKey, expanded: boolean): void {
  try {
    localStorage.setItem(
      `${STORAGE_KEY_PREFIX}${pageKey}`,
      expanded ? 'expanded' : 'collapsed'
    )
  } catch {
    // Игнорируем ошибки localStorage
  }
}

/**
 * Компонент информационного блока для страниц.
 *
 * Отображает описание назначения страницы и список ключевых возможностей.
 * Состояние expand/collapse сохраняется в localStorage.
 *
 * @param pageKey - уникальный ключ страницы
 * @param title - заголовок страницы
 * @param description - краткое описание назначения
 * @param features - список ключевых возможностей
 */
export function PageInfoBlock({
  pageKey,
  title,
  description,
  features,
}: PageInfoBlockProps) {
  const { token } = theme.useToken()
  const [activeKey, setActiveKey] = useState<string[]>(() =>
    getStoredState(pageKey) ? ['info'] : []
  )

  // Синхронизация при смене pageKey (переход между страницами)
  useEffect(() => {
    setActiveKey(getStoredState(pageKey) ? ['info'] : [])
  }, [pageKey])

  // Обработчик изменения состояния collapse
  const handleChange = useCallback(
    (keys: string | string[]) => {
      const keyArray = Array.isArray(keys) ? keys : [keys]
      setActiveKey(keyArray)
      setStoredState(pageKey, keyArray.includes('info'))
    },
    [pageKey]
  )

  // Определяем, развёрнут ли блок
  const isExpanded = activeKey.includes('info')

  // Содержимое блока
  const contentItems = [
    {
      key: 'info',
      label: (
        <Space>
          <InfoCircleOutlined style={{ color: token.colorPrimary }} />
          <Text strong>
            {title}
          </Text>
          <Text type="secondary">— {description}</Text>
        </Space>
      ),
      children: (
        <ul style={{ margin: 0, paddingLeft: 20 }} data-testid="page-info-features">
          {features.map((feature, index) => (
            <li key={index} style={{ marginBottom: 4 }}>
              <Text>{feature}</Text>
            </li>
          ))}
        </ul>
      ),
    },
  ]

  return (
    <Collapse
      activeKey={activeKey}
      onChange={handleChange}
      expandIcon={({ isActive }) =>
        isActive ? <UpOutlined /> : <DownOutlined />
      }
      expandIconPosition="end"
      items={contentItems}
      style={{
        marginBottom: 16,
        backgroundColor: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
      }}
      data-testid="page-info-block"
      data-expanded={isExpanded}
    />
  )
}
