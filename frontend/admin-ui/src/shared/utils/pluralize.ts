// Утилиты для склонения слов на русском языке

/**
 * Склонение слова "маршрут" в зависимости от числа.
 *
 * Примеры:
 * - 1 маршрут
 * - 2-4 маршрута
 * - 5-20 маршрутов
 * - 21 маршрут
 * - 22-24 маршрута
 * - 25-30 маршрутов
 *
 * @param count Количество
 * @returns Строка с числом и склонённым словом
 */
export function pluralizeRoutes(count: number): string {
  const lastTwo = count % 100
  const lastOne = count % 10

  if (lastTwo >= 11 && lastTwo <= 19) {
    return `${count} маршрутов`
  }

  if (lastOne === 1) {
    return `${count} маршрут`
  }

  if (lastOne >= 2 && lastOne <= 4) {
    return `${count} маршрута`
  }

  return `${count} маршрутов`
}

/**
 * Универсальная функция склонения слов на русском языке.
 *
 * @param count Количество
 * @param one Форма для 1 (маршрут, пользователь, запись)
 * @param few Форма для 2-4 (маршрута, пользователя, записи)
 * @param many Форма для 5+ (маршрутов, пользователей, записей)
 * @returns Строка с числом и склонённым словом
 */
export function pluralize(
  count: number,
  one: string,
  few: string,
  many: string
): string {
  const lastTwo = count % 100
  const lastOne = count % 10

  if (lastTwo >= 11 && lastTwo <= 19) {
    return `${count} ${many}`
  }

  if (lastOne === 1) {
    return `${count} ${one}`
  }

  if (lastOne >= 2 && lastOne <= 4) {
    return `${count} ${few}`
  }

  return `${count} ${many}`
}
