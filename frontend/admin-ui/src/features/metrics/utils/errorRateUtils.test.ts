// Тесты для errorRateUtils (Story 6.5)
import { describe, it, expect } from 'vitest'
import {
  getErrorRateStatus,
  getErrorRateColor,
  getErrorRateTagColor,
  ERROR_RATE_THRESHOLDS,
  ERROR_RATE_COLORS,
  ERROR_RATE_TAG_COLORS,
} from './errorRateUtils'

describe('errorRateUtils', () => {
  describe('getErrorRateStatus', () => {
    it('возвращает healthy для error rate < 1%', () => {
      expect(getErrorRateStatus(0)).toBe('healthy')
      expect(getErrorRateStatus(0.005)).toBe('healthy')
      expect(getErrorRateStatus(0.009)).toBe('healthy')
    })

    it('возвращает warning для error rate 1-5%', () => {
      expect(getErrorRateStatus(0.01)).toBe('warning')
      expect(getErrorRateStatus(0.03)).toBe('warning')
      expect(getErrorRateStatus(0.049)).toBe('warning')
    })

    it('возвращает critical для error rate > 5%', () => {
      expect(getErrorRateStatus(0.05)).toBe('critical')
      expect(getErrorRateStatus(0.1)).toBe('critical')
      expect(getErrorRateStatus(1)).toBe('critical')
    })

    it('корректно обрабатывает граничные значения', () => {
      // Ровно 1% — это уже warning
      expect(getErrorRateStatus(ERROR_RATE_THRESHOLDS.HEALTHY)).toBe('warning')
      // Ровно 5% — это уже critical
      expect(getErrorRateStatus(ERROR_RATE_THRESHOLDS.WARNING)).toBe('critical')
    })
  })

  describe('getErrorRateColor', () => {
    it('возвращает зелёный HEX для healthy', () => {
      expect(getErrorRateColor(0.005)).toBe(ERROR_RATE_COLORS.healthy)
      expect(getErrorRateColor(0.005)).toBe('#52c41a')
    })

    it('возвращает жёлтый HEX для warning', () => {
      expect(getErrorRateColor(0.03)).toBe(ERROR_RATE_COLORS.warning)
      expect(getErrorRateColor(0.03)).toBe('#faad14')
    })

    it('возвращает красный HEX для critical', () => {
      expect(getErrorRateColor(0.08)).toBe(ERROR_RATE_COLORS.critical)
      expect(getErrorRateColor(0.08)).toBe('#f5222d')
    })
  })

  describe('getErrorRateTagColor', () => {
    it('возвращает green для healthy', () => {
      expect(getErrorRateTagColor(0.005)).toBe(ERROR_RATE_TAG_COLORS.healthy)
      expect(getErrorRateTagColor(0.005)).toBe('green')
    })

    it('возвращает orange для warning', () => {
      expect(getErrorRateTagColor(0.03)).toBe(ERROR_RATE_TAG_COLORS.warning)
      expect(getErrorRateTagColor(0.03)).toBe('orange')
    })

    it('возвращает red для critical', () => {
      expect(getErrorRateTagColor(0.08)).toBe(ERROR_RATE_TAG_COLORS.critical)
      expect(getErrorRateTagColor(0.08)).toBe('red')
    })
  })

  describe('константы', () => {
    it('ERROR_RATE_THRESHOLDS содержит корректные значения', () => {
      expect(ERROR_RATE_THRESHOLDS.HEALTHY).toBe(0.01)
      expect(ERROR_RATE_THRESHOLDS.WARNING).toBe(0.05)
    })
  })
})
