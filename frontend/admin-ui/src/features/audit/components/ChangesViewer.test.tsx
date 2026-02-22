// Тесты для ChangesViewer (Story 7.5, AC3; Story 10.8)
import { describe, it, expect, vi } from 'vitest'
import { screen, render } from '@testing-library/react'
import { ChangesViewer } from './ChangesViewer'

// Мокаем ThemeProvider
vi.mock('@/shared/providers/ThemeProvider', () => ({
  useThemeContext: () => ({
    theme: 'light',
    isDark: false,
    isLight: true,
    toggle: vi.fn(),
    setTheme: vi.fn(),
  }),
}))

describe('ChangesViewer', () => {
  const beforeData = {
    path: '/api/v1/old',
    upstreamUrl: 'http://old-service:8080',
    methods: ['GET'],
  }

  const afterData = {
    path: '/api/v1/new',
    upstreamUrl: 'http://new-service:8080',
    methods: ['GET', 'POST'],
  }

  it('показывает Before/After сравнение для updated событий', () => {
    render(
      <ChangesViewer
        before={beforeData}
        after={afterData}
        action="updated"
      />
    )

    // Заголовки разделов
    expect(screen.getByText('До изменения')).toBeInTheDocument()
    expect(screen.getByText('После изменения')).toBeInTheDocument()

    // Данные отображаются
    expect(screen.getByText(/old-service/)).toBeInTheDocument()
    expect(screen.getByText(/new-service/)).toBeInTheDocument()
  })

  it('показывает только After для created событий', () => {
    render(
      <ChangesViewer
        before={null}
        after={afterData}
        action="created"
      />
    )

    // Заголовок created
    expect(screen.getByText('Созданные данные')).toBeInTheDocument()
    // Нет заголовка До изменения
    expect(screen.queryByText('До изменения')).not.toBeInTheDocument()
    // Данные after отображаются
    expect(screen.getByText(/new-service/)).toBeInTheDocument()
  })

  it('показывает только Before для deleted событий', () => {
    render(
      <ChangesViewer
        before={beforeData}
        after={null}
        action="deleted"
      />
    )

    // Заголовок deleted
    expect(screen.getByText('Удалённые данные')).toBeInTheDocument()
    // Нет заголовка После изменения
    expect(screen.queryByText('После изменения')).not.toBeInTheDocument()
    // Данные before отображаются
    expect(screen.getByText(/old-service/)).toBeInTheDocument()
  })

  it('показывает empty state если нет данных', () => {
    render(
      <ChangesViewer
        before={null}
        after={null}
        action="updated"
      />
    )

    expect(screen.getByText('Нет данных об изменениях')).toBeInTheDocument()
  })

  it('корректно форматирует JSON с отступами', () => {
    render(
      <ChangesViewer
        before={beforeData}
        after={afterData}
        action="updated"
      />
    )

    // JSON должен содержать отформатированные данные
    expect(screen.getByText(/\/api\/v1\/old/)).toBeInTheDocument()
    expect(screen.getByText(/\/api\/v1\/new/)).toBeInTheDocument()
  })

  it('показывает только After для approved событий', () => {
    render(
      <ChangesViewer
        before={null}
        after={afterData}
        action="approved"
      />
    )

    expect(screen.getByText('Одобренные данные')).toBeInTheDocument()
    expect(screen.queryByText('До изменения')).not.toBeInTheDocument()
  })

  it('показывает только Before для rejected событий', () => {
    render(
      <ChangesViewer
        before={beforeData}
        after={null}
        action="rejected"
      />
    )

    expect(screen.getByText('Отклонённые данные')).toBeInTheDocument()
    expect(screen.queryByText('После изменения')).not.toBeInTheDocument()
  })

  it('показывает только After для submitted событий', () => {
    render(
      <ChangesViewer
        before={null}
        after={afterData}
        action="submitted"
      />
    )

    expect(screen.getByText('Отправленные данные')).toBeInTheDocument()
    expect(screen.queryByText('До изменения')).not.toBeInTheDocument()
  })

  it('показывает только After для published событий', () => {
    render(
      <ChangesViewer
        before={null}
        after={afterData}
        action="published"
      />
    )

    expect(screen.getByText('Опубликованные данные')).toBeInTheDocument()
    expect(screen.queryByText('До изменения')).not.toBeInTheDocument()
  })

  // Story 10.8: Generic режим для changes без before/after структуры
  describe('generic режим (Story 10.8)', () => {
    it('отображает generic JSON для approved без before/after', () => {
      // Backend format: {previousStatus, newStatus, approvedAt}
      const approvedChanges = {
        previousStatus: 'pending',
        newStatus: 'published',
        approvedAt: '2026-02-22T10:30:00Z',
      }

      render(
        <ChangesViewer
          changes={approvedChanges}
          action="approved"
        />
      )

      expect(screen.getByText('Детали изменения')).toBeInTheDocument()
      expect(screen.getByText(/previousStatus/)).toBeInTheDocument()
      expect(screen.getByText(/pending/)).toBeInTheDocument()
      expect(screen.getByText(/published/)).toBeInTheDocument()
    })

    it('отображает generic JSON для rejected без before/after', () => {
      const rejectedChanges = {
        previousStatus: 'pending',
        newStatus: 'rejected',
        rejectedAt: '2026-02-22T11:00:00Z',
        rejectionReason: 'Неверная конфигурация',
      }

      render(
        <ChangesViewer
          changes={rejectedChanges}
          action="rejected"
        />
      )

      expect(screen.getByText('Детали изменения')).toBeInTheDocument()
      expect(screen.getByText(/rejectionReason/)).toBeInTheDocument()
      expect(screen.getByText(/Неверная конфигурация/)).toBeInTheDocument()
    })

    it('отображает generic JSON для submitted без before/after', () => {
      const submittedChanges = {
        newStatus: 'pending',
        submittedAt: '2026-02-22T09:00:00Z',
      }

      render(
        <ChangesViewer
          changes={submittedChanges}
          action="submitted"
        />
      )

      expect(screen.getByText('Детали изменения')).toBeInTheDocument()
      expect(screen.getByText(/newStatus/)).toBeInTheDocument()
      expect(screen.getByText(/pending/)).toBeInTheDocument()
    })

    it('отображает generic JSON для route.rolledback без before/after', () => {
      const rolledbackChanges = {
        previousStatus: 'published',
        newStatus: 'draft',
        rolledbackAt: '2026-02-22T12:00:00Z',
        rolledbackBy: 'admin',
      }

      render(
        <ChangesViewer
          changes={rolledbackChanges}
          action="route.rolledback"
        />
      )

      expect(screen.getByText('Детали изменения')).toBeInTheDocument()
      expect(screen.getByText(/rolledbackBy/)).toBeInTheDocument()
      expect(screen.getByText(/admin/)).toBeInTheDocument()
    })

    it('сохраняет diff режим для updated с before/after', () => {
      const updatedChanges = {
        before: {
          path: '/api/v1/old',
          upstreamUrl: 'http://old-service:8080',
        },
        after: {
          path: '/api/v1/new',
          upstreamUrl: 'http://new-service:8080',
        },
      }

      render(
        <ChangesViewer
          changes={updatedChanges}
          action="updated"
        />
      )

      // Должен показывать diff режим, не generic
      expect(screen.getByText('До изменения')).toBeInTheDocument()
      expect(screen.getByText('После изменения')).toBeInTheDocument()
      expect(screen.queryByText('Детали изменения')).not.toBeInTheDocument()
    })

    it('сохраняет after-only режим для created с after', () => {
      const createdChanges = {
        after: {
          path: '/api/v1/new',
          upstreamUrl: 'http://new-service:8080',
        },
      }

      render(
        <ChangesViewer
          changes={createdChanges}
          action="created"
        />
      )

      // Должен показывать after-only режим
      expect(screen.getByText('Созданные данные')).toBeInTheDocument()
      expect(screen.queryByText('Детали изменения')).not.toBeInTheDocument()
    })
  })
})
