import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from '@layouts/MainLayout'

function App() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<Navigate to="/routes" replace />} />
        <Route path="routes" element={<div>Routes Management</div>} />
        <Route path="rate-limits" element={<div>Rate Limits Management</div>} />
        <Route path="approvals" element={<div>Approvals</div>} />
        <Route path="audit" element={<div>Audit Logs</div>} />
      </Route>
      <Route path="/login" element={<div>Login Page</div>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
