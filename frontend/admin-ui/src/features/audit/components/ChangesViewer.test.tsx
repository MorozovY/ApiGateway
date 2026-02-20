// Тесты для ChangesViewer (Story 7.5, AC3)
import { describe, it, expect } from 'vitest'
import { screen, render } from '@testing-library/react'
import { ChangesViewer } from './ChangesViewer'

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
})
