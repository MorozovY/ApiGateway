// Unit тесты для permission helpers (Story 11.6)
import { describe, it, expect } from 'vitest'
import {
  isAdmin,
  isDeveloper,
  isAdminOrSecurity,
  canApprove,
  canRollback,
  canDelete,
  canModify,
} from './rolePermissions'
import type { MinimalUser, MinimalRoute } from './rolePermissions'

describe('isAdmin', () => {
  it('возвращает true для admin', () => {
    const user: MinimalUser = { role: 'admin' }
    expect(isAdmin(user)).toBe(true)
  })

  it('возвращает false для security', () => {
    const user: MinimalUser = { role: 'security' }
    expect(isAdmin(user)).toBe(false)
  })

  it('возвращает false для developer', () => {
    const user: MinimalUser = { role: 'developer' }
    expect(isAdmin(user)).toBe(false)
  })

  it('возвращает false для undefined user', () => {
    expect(isAdmin(undefined)).toBe(false)
  })

  it('возвращает false для user без role', () => {
    const user: MinimalUser = { userId: '123' }
    expect(isAdmin(user)).toBe(false)
  })
})

describe('isDeveloper', () => {
  it('возвращает true для developer', () => {
    const user: MinimalUser = { role: 'developer' }
    expect(isDeveloper(user)).toBe(true)
  })

  it('возвращает false для security', () => {
    const user: MinimalUser = { role: 'security' }
    expect(isDeveloper(user)).toBe(false)
  })

  it('возвращает false для admin', () => {
    const user: MinimalUser = { role: 'admin' }
    expect(isDeveloper(user)).toBe(false)
  })

  it('возвращает false для undefined user', () => {
    expect(isDeveloper(undefined)).toBe(false)
  })

  it('возвращает false для user без role', () => {
    const user: MinimalUser = { userId: '123' }
    expect(isDeveloper(user)).toBe(false)
  })
})

describe('isAdminOrSecurity', () => {
  it('возвращает true для admin', () => {
    const user: MinimalUser = { role: 'admin' }
    expect(isAdminOrSecurity(user)).toBe(true)
  })

  it('возвращает true для security', () => {
    const user: MinimalUser = { role: 'security' }
    expect(isAdminOrSecurity(user)).toBe(true)
  })

  it('возвращает false для developer', () => {
    const user: MinimalUser = { role: 'developer' }
    expect(isAdminOrSecurity(user)).toBe(false)
  })

  it('возвращает false для undefined user', () => {
    expect(isAdminOrSecurity(undefined)).toBe(false)
  })

  it('возвращает false для user без role', () => {
    const user: MinimalUser = { userId: '123' }
    expect(isAdminOrSecurity(user)).toBe(false)
  })
})

describe('canApprove', () => {
  it('возвращает true для security', () => {
    const user: MinimalUser = { role: 'security' }
    expect(canApprove(user)).toBe(true)
  })

  it('возвращает true для admin', () => {
    const user: MinimalUser = { role: 'admin' }
    expect(canApprove(user)).toBe(true)
  })

  it('возвращает false для developer', () => {
    const user: MinimalUser = { role: 'developer' }
    expect(canApprove(user)).toBe(false)
  })

  it('возвращает false для undefined user', () => {
    expect(canApprove(undefined)).toBe(false)
  })
})

describe('canRollback', () => {
  it('возвращает true для Security на published маршруте', () => {
    const route: MinimalRoute = { status: 'published' }
    const user: MinimalUser = { role: 'security' }
    expect(canRollback(route, user)).toBe(true)
  })

  it('возвращает true для Admin на published маршруте', () => {
    const route: MinimalRoute = { status: 'published' }
    const user: MinimalUser = { role: 'admin' }
    expect(canRollback(route, user)).toBe(true)
  })

  it('возвращает false для Developer на published маршруте', () => {
    const route: MinimalRoute = { status: 'published' }
    const user: MinimalUser = { role: 'developer' }
    expect(canRollback(route, user)).toBe(false)
  })

  it('возвращает false для Admin на draft маршруте', () => {
    const route: MinimalRoute = { status: 'draft' }
    const user: MinimalUser = { role: 'admin' }
    expect(canRollback(route, user)).toBe(false)
  })

  it('возвращает false для Security на pending маршруте', () => {
    const route: MinimalRoute = { status: 'pending' }
    const user: MinimalUser = { role: 'security' }
    expect(canRollback(route, user)).toBe(false)
  })

  it('возвращает false для Security на rejected маршруте', () => {
    const route: MinimalRoute = { status: 'rejected' }
    const user: MinimalUser = { role: 'security' }
    expect(canRollback(route, user)).toBe(false)
  })

  it('возвращает false для undefined user', () => {
    const route: MinimalRoute = { status: 'published' }
    expect(canRollback(route, undefined)).toBe(false)
  })
})

describe('canDelete', () => {
  const authorId = 'author-123'

  it('возвращает true для автора на draft маршруте', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: authorId }
    const user: MinimalUser = { userId: authorId, role: 'developer' }
    expect(canDelete(route, user)).toBe(true)
  })

  it('возвращает true для Admin на draft маршруте (не автор)', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: 'other-user' }
    const user: MinimalUser = { userId: authorId, role: 'admin' }
    expect(canDelete(route, user)).toBe(true)
  })

  it('возвращает false для не-автора developer на draft маршруте', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: 'other-user' }
    const user: MinimalUser = { userId: authorId, role: 'developer' }
    expect(canDelete(route, user)).toBe(false)
  })

  it('возвращает false для Security (не автор) на draft маршруте', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: 'other-user' }
    const user: MinimalUser = { userId: authorId, role: 'security' }
    expect(canDelete(route, user)).toBe(false)
  })

  it('возвращает false для автора на published маршруте', () => {
    const route: MinimalRoute = { status: 'published', createdBy: authorId }
    const user: MinimalUser = { userId: authorId, role: 'developer' }
    expect(canDelete(route, user)).toBe(false)
  })

  it('возвращает false для автора на pending маршруте', () => {
    const route: MinimalRoute = { status: 'pending', createdBy: authorId }
    const user: MinimalUser = { userId: authorId, role: 'developer' }
    expect(canDelete(route, user)).toBe(false)
  })

  it('возвращает false для Admin на published маршруте', () => {
    const route: MinimalRoute = { status: 'published', createdBy: 'other-user' }
    const user: MinimalUser = { userId: authorId, role: 'admin' }
    expect(canDelete(route, user)).toBe(false)
  })

  it('возвращает false для undefined user', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: authorId }
    expect(canDelete(route, undefined)).toBe(false)
  })
})

describe('canModify', () => {
  const authorId = 'author-123'

  it('возвращает true для автора на draft маршруте', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: authorId }
    const user: MinimalUser = { userId: authorId, role: 'developer' }
    expect(canModify(route, user)).toBe(true)
  })

  it('возвращает true для Admin на draft маршруте (не автор)', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: 'other-user' }
    const user: MinimalUser = { userId: authorId, role: 'admin' }
    expect(canModify(route, user)).toBe(true)
  })

  it('возвращает false для не-автора developer на draft маршруте', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: 'other-user' }
    const user: MinimalUser = { userId: authorId, role: 'developer' }
    expect(canModify(route, user)).toBe(false)
  })

  it('возвращает false для Security (не автор) на draft маршруте', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: 'other-user' }
    const user: MinimalUser = { userId: authorId, role: 'security' }
    expect(canModify(route, user)).toBe(false)
  })

  it('возвращает false для автора на published маршруте', () => {
    const route: MinimalRoute = { status: 'published', createdBy: authorId }
    const user: MinimalUser = { userId: authorId, role: 'developer' }
    expect(canModify(route, user)).toBe(false)
  })

  it('возвращает false для автора на pending маршруте', () => {
    const route: MinimalRoute = { status: 'pending', createdBy: authorId }
    const user: MinimalUser = { userId: authorId, role: 'developer' }
    expect(canModify(route, user)).toBe(false)
  })

  it('возвращает false для Admin на published маршруте', () => {
    const route: MinimalRoute = { status: 'published', createdBy: 'other-user' }
    const user: MinimalUser = { userId: authorId, role: 'admin' }
    expect(canModify(route, user)).toBe(false)
  })

  it('возвращает false для undefined user', () => {
    const route: MinimalRoute = { status: 'draft', createdBy: authorId }
    expect(canModify(route, undefined)).toBe(false)
  })
})
