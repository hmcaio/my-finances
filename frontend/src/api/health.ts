// Local dev only: backend is always http://localhost:8080 (PRD S7.1 - localhost-only, no
// LAN/internet exposure). A real env-based config is not needed at this scaffolding stage.
const API_BASE_URL = 'http://localhost:8080'

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
  const response = await fetch(`${API_BASE_URL}/api/health`)
  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`)
  }
  return response.json() as Promise<HealthResponse>
}
