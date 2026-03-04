// Тесты для pageDescriptions конфигурации (Story 15.4)
import { describe, it, expect } from 'vitest'
import { PAGE_DESCRIPTIONS, type PageKey } from './pageDescriptions'

describe('PAGE_DESCRIPTIONS', () => {
  // Список всех 10 страниц системы (AC3)
  const expectedPageKeys: PageKey[] = [
    'dashboard',
    'routes',
    'metrics',
    'approvals',
    'audit',
    'integrations',
    'users',
    'consumers',
    'rateLimits',
    'test',
  ]

  it('содержит описания для всех 10 страниц (AC3)', () => {
    const actualKeys = Object.keys(PAGE_DESCRIPTIONS)
    expect(actualKeys).toHaveLength(10)
    expectedPageKeys.forEach((key) => {
      expect(PAGE_DESCRIPTIONS).toHaveProperty(key)
    })
  })

  it('каждая страница имеет непустой title', () => {
    expectedPageKeys.forEach((key) => {
      const desc = PAGE_DESCRIPTIONS[key]
      expect(desc.title).toBeDefined()
      expect(desc.title.trim().length).toBeGreaterThan(0)
    })
  })

  it('каждая страница имеет непустое description', () => {
    expectedPageKeys.forEach((key) => {
      const desc = PAGE_DESCRIPTIONS[key]
      expect(desc.description).toBeDefined()
      expect(desc.description.trim().length).toBeGreaterThan(0)
    })
  })

  it('каждая страница имеет непустой массив features', () => {
    expectedPageKeys.forEach((key) => {
      const desc = PAGE_DESCRIPTIONS[key]
      expect(desc.features).toBeDefined()
      expect(Array.isArray(desc.features)).toBe(true)
      expect(desc.features.length).toBeGreaterThan(0)
    })
  })

  it('все features непустые строки', () => {
    expectedPageKeys.forEach((key) => {
      const desc = PAGE_DESCRIPTIONS[key]
      desc.features.forEach((feature, index) => {
        expect(
          feature.trim().length,
          `${key}.features[${index}] должен быть непустой строкой`
        ).toBeGreaterThan(0)
      })
    })
  })

  it('описания на русском языке (содержат кириллицу)', () => {
    const cyrillicRegex = /[а-яА-ЯёЁ]/
    expectedPageKeys.forEach((key) => {
      const desc = PAGE_DESCRIPTIONS[key]
      // Description должен быть на русском
      expect(
        cyrillicRegex.test(desc.description),
        `${key}.description должен содержать русский текст`
      ).toBe(true)
      // Хотя бы один feature на русском
      const hasRussianFeature = desc.features.some((f) => cyrillicRegex.test(f))
      expect(
        hasRussianFeature,
        `${key}.features должны содержать русский текст`
      ).toBe(true)
    })
  })
})
