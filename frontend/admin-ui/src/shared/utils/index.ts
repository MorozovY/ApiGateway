// Публичный API shared utils
export { pluralizeRoutes, pluralize } from './pluralize'
export {
  isAdmin,
  isDeveloper,
  isAdminOrSecurity,
  canApprove,
  canRollback,
  canDelete,
  canModify,
  type MinimalUser,
  type MinimalRoute,
} from './rolePermissions'
