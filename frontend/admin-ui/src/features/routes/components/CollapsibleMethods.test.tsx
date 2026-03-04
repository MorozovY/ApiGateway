// Тесты для компонента CollapsibleMethods (Story 16.4, AC3)
import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { CollapsibleMethods } from './CollapsibleMethods'

describe('CollapsibleMethods (Story 16.4 AC3)', () => {
  describe('когда методов <= 3', () => {
    it('показывает все методы без кнопки сворачивания', () => {
      render(<CollapsibleMethods methods={['GET', 'POST']} />)

      expect(screen.getByText('GET')).toBeInTheDocument()
      expect(screen.getByText('POST')).toBeInTheDocument()
      expect(screen.queryByTestId('methods-expand-button')).not.toBeInTheDocument()
    })

    it('показывает 3 метода без кнопки', () => {
      render(<CollapsibleMethods methods={['GET', 'POST', 'PUT']} />)

      expect(screen.getByText('GET')).toBeInTheDocument()
      expect(screen.getByText('POST')).toBeInTheDocument()
      expect(screen.getByText('PUT')).toBeInTheDocument()
      expect(screen.queryByTestId('methods-expand-button')).not.toBeInTheDocument()
    })
  })

  describe('когда методов > 3', () => {
    it('показывает первые 3 метода и кнопку "ещё +N"', () => {
      render(<CollapsibleMethods methods={['GET', 'POST', 'PUT', 'DELETE', 'PATCH']} />)

      // Первые 3 метода видны
      expect(screen.getByText('GET')).toBeInTheDocument()
      expect(screen.getByText('POST')).toBeInTheDocument()
      expect(screen.getByText('PUT')).toBeInTheDocument()

      // Остальные скрыты
      expect(screen.queryByText('DELETE')).not.toBeInTheDocument()
      expect(screen.queryByText('PATCH')).not.toBeInTheDocument()

      // Кнопка развёртывания видна с правильным числом
      expect(screen.getByText('ещё +2')).toBeInTheDocument()
    })

    it('разворачивает все методы по клику', () => {
      render(<CollapsibleMethods methods={['GET', 'POST', 'PUT', 'DELETE', 'PATCH']} />)

      // Кликаем на кнопку развёртывания
      const expandButton = screen.getByTestId('methods-expand-button')
      fireEvent.click(expandButton)

      // Все методы видны
      expect(screen.getByText('GET')).toBeInTheDocument()
      expect(screen.getByText('POST')).toBeInTheDocument()
      expect(screen.getByText('PUT')).toBeInTheDocument()
      expect(screen.getByText('DELETE')).toBeInTheDocument()
      expect(screen.getByText('PATCH')).toBeInTheDocument()

      // Кнопка развёртывания скрыта, видна кнопка сворачивания
      expect(screen.queryByTestId('methods-expand-button')).not.toBeInTheDocument()
      expect(screen.getByTestId('methods-collapse-button')).toBeInTheDocument()
    })

    it('сворачивает методы по клику на "свернуть"', () => {
      render(<CollapsibleMethods methods={['GET', 'POST', 'PUT', 'DELETE', 'PATCH']} />)

      // Разворачиваем
      fireEvent.click(screen.getByTestId('methods-expand-button'))

      // Сворачиваем
      fireEvent.click(screen.getByTestId('methods-collapse-button'))

      // Снова только 3 метода видны
      expect(screen.getByText('GET')).toBeInTheDocument()
      expect(screen.getByText('POST')).toBeInTheDocument()
      expect(screen.getByText('PUT')).toBeInTheDocument()
      expect(screen.queryByText('DELETE')).not.toBeInTheDocument()
      expect(screen.queryByText('PATCH')).not.toBeInTheDocument()

      // Кнопка развёртывания снова видна
      expect(screen.getByTestId('methods-expand-button')).toBeInTheDocument()
    })
  })

  describe('кастомный maxVisible', () => {
    it('использует переданное значение maxVisible', () => {
      render(<CollapsibleMethods methods={['GET', 'POST', 'PUT', 'DELETE']} maxVisible={2} />)

      // Только 2 метода видны
      expect(screen.getByText('GET')).toBeInTheDocument()
      expect(screen.getByText('POST')).toBeInTheDocument()
      expect(screen.queryByText('PUT')).not.toBeInTheDocument()

      // Кнопка показывает +2
      expect(screen.getByText('ещё +2')).toBeInTheDocument()
    })
  })

  describe('пустой массив методов', () => {
    it('не рендерит ничего для пустого массива', () => {
      render(<CollapsibleMethods methods={[]} />)

      // Проверяем что нет тегов методов и кнопок
      expect(screen.queryByRole('button')).not.toBeInTheDocument()
      // Проверяем отсутствие стандартных методов
      expect(screen.queryByText('GET')).not.toBeInTheDocument()
      expect(screen.queryByText('POST')).not.toBeInTheDocument()
    })
  })
})
