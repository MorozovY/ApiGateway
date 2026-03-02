import type { Page } from '@playwright/test'

/**
 * Фильтрует таблицу по тексту используя поле поиска.
 *
 * Поддерживает различные варианты placeholder для Search/Поиск.
 * После ввода текста ждёт debounce (300ms) для применения фильтра.
 *
 * @param page - Playwright Page объект
 * @param searchText - Текст для поиска/фильтрации
 * @returns true если поле поиска найдено и заполнено, false если поля нет
 */
export async function filterTableByName(page: Page, searchText: string): Promise<boolean> {
  // Пробуем разные варианты селекторов для поля поиска
  const searchSelectors = [
    'input[placeholder*="Search"]',
    'input[placeholder*="Поиск"]',
    'input[placeholder*="search"]',
    '[data-testid="search-input"]',
    '.ant-input-search input',
  ]

  for (const selector of searchSelectors) {
    const searchInput = page.locator(selector).first()
    if (await searchInput.isVisible({ timeout: 5000 }).catch(() => false)) {
      await searchInput.fill(searchText)
      // Ждём применения фильтра — debounce + network
      await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {
        // Fallback если networkidle не сработал (клиентская фильтрация)
      })
      return true
    }
  }

  return false
}

/**
 * Ожидает появления строки таблицы с указанным текстом.
 *
 * @param page - Playwright Page объект
 * @param text - Текст который должен быть в строке таблицы
 * @param timeout - Таймаут ожидания в мс (по умолчанию 10000)
 */
export async function waitForTableRow(page: Page, text: string, timeout = 10_000): Promise<void> {
  await page.locator(`tr:has-text("${text}")`).waitFor({ state: 'visible', timeout })
}

/**
 * Проверяет что строка таблицы с указанным текстом отсутствует.
 *
 * @param page - Playwright Page объект
 * @param text - Текст который НЕ должен быть в таблице
 * @param timeout - Таймаут ожидания в мс (по умолчанию 5000)
 */
export async function expectTableRowNotVisible(page: Page, text: string, timeout = 5_000): Promise<void> {
  await page.locator(`tr:has-text("${text}")`).waitFor({ state: 'hidden', timeout })
}
