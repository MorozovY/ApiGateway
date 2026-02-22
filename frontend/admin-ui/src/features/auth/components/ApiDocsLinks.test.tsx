// Тесты для ApiDocsLinks компонента (Story 10.6)
import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithMockAuth } from '@/test/test-utils'
import { ApiDocsLinks } from './ApiDocsLinks'

describe('ApiDocsLinks', () => {
  // AC1: Отображение ссылки на Swagger UI
  describe('рендеринг (AC1)', () => {
    it('отображает секцию API документации', () => {
      renderWithMockAuth(<ApiDocsLinks />)

      expect(screen.getByTestId('api-docs-links')).toBeInTheDocument()
      expect(screen.getByText('📚 API документация')).toBeInTheDocument()
    })

    it('отображает ссылку на Swagger UI', () => {
      renderWithMockAuth(<ApiDocsLinks />)

      expect(screen.getByTestId('swagger-link')).toBeInTheDocument()
      expect(screen.getByText('Gateway Admin API (Swagger)')).toBeInTheDocument()
    })

    it('ссылка имеет корректный URL /swagger-ui.html', () => {
      renderWithMockAuth(<ApiDocsLinks />)

      const link = screen.getByTestId('swagger-link')
      expect(link).toHaveAttribute('href', '/swagger-ui.html')
    })
  })

  // AC2: Ссылка открывается в новой вкладке
  describe('открытие в новой вкладке (AC2)', () => {
    it('ссылка открывается в новой вкладке', () => {
      renderWithMockAuth(<ApiDocsLinks />)

      const link = screen.getByTestId('swagger-link')
      expect(link).toHaveAttribute('target', '_blank')
    })

    it('ссылка имеет rel="noopener noreferrer" для безопасности', () => {
      renderWithMockAuth(<ApiDocsLinks />)

      const link = screen.getByTestId('swagger-link')
      expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    })
  })

  // AC3: Визуальное оформление
  describe('визуальное оформление (AC3)', () => {
    it('секция имеет разделитель (Divider)', () => {
      renderWithMockAuth(<ApiDocsLinks />)

      // Divider отображает заголовок секции
      const divider = screen.getByText('📚 API документация').closest('.ant-divider')
      expect(divider).toBeInTheDocument()
    })
  })
})
