import axios from 'axios'

/**
 * Single Axios instance shared by every typed client under `src/api` — the one place that owns
 * the backend's base URL (including the `/api` prefix, so call sites use bare relative paths like
 * `/categories`). Configurable via `VITE_API_BASE_URL` (see `.env.development`/`.env.production`,
 * F014 spec): dev points at the natively-run backend directly, prod is a relative `/api` that
 * nginx reverse-proxies same-origin (no CORS needed in prod).
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})
