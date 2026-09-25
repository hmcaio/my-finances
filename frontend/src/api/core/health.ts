import { apiClient } from './client'
import { unwrap } from './apiError'

export interface HealthResponse {
  status: string
  timestamp: string
}

/**
 * Calls the backend's F001 health-check endpoint (see
 * infrastructure/web/HealthController on the backend) to confirm frontend-to-backend
 * connectivity. Not a product endpoint - F002+ add real typed clients under src/api,
 * generated against src/api/generated/schema.ts (see `npm run generate-api-types`).
 */
export async function getHealth(): Promise<HealthResponse> {
  return unwrap(apiClient.get<HealthResponse>('/health'))
}
