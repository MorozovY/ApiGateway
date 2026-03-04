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
export { isMacOS, getModifierKey, formatShortcut } from './keyboard'  // Story 16.9
export { getCurrentWorkflowStep, WORKFLOW_STEP } from './workflowStep'  // Story 16.10
export { highlightSearchTerm } from './highlight'  // Story 16.10 — code review refactoring
