// Ссылки на API документацию для страницы логина (Story 10.6)
import { Typography, Divider } from 'antd'
import { FileTextOutlined } from '@ant-design/icons'

const { Text, Link } = Typography

/**
 * Ссылки на API документацию (Swagger UI) на странице логина.
 *
 * AC1: Отображает ссылку на Swagger UI для Gateway Admin API.
 * AC2: Ссылки открываются в новой вкладке.
 * AC3: Визуально отделены от Demo Credentials.
 */
export function ApiDocsLinks() {
  return (
    <div style={{ marginTop: 24 }} data-testid="api-docs-links">
      <Divider style={{ margin: '16px 0' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>📚 API документация</Text>
      </Divider>

      <div style={{ textAlign: 'center' }}>
        <Link
          href="/swagger-ui.html"
          target="_blank"
          rel="noopener noreferrer"
          data-testid="swagger-link"
        >
          <FileTextOutlined /> Gateway Admin API (Swagger)
        </Link>
      </div>
    </div>
  )
}
