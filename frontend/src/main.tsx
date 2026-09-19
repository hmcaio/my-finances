import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { installGlobalErrorLogging, reactRootErrorHandlers } from './utils/globalErrorLogging'

installGlobalErrorLogging()

createRoot(document.getElementById('root')!, reactRootErrorHandlers).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
