import { http, HttpResponse } from 'msw'
import type { AuditLogEntry } from '../../api/auditLog/auditLog'

const AUDIT_LOG_URL = '/api/audit-log'

/**
 * Seed data returned by the default `GET /api/audit-log` handler below (F025 spec). Read-only
 * feature - unlike every other handler file, there is no store/mutation here, nothing writes an
 * entry back; a test that needs a different set overrides this handler with `server.use(...)`.
 * Spans multiple days, entity types, actions and origins so filter/grouping tests have something
 * to discriminate on.
 */
export const seedAuditLogEntries: AuditLogEntry[] = [
  {
    id: 'audit-3',
    occurredAt: '2026-03-16T14:30:00Z',
    entityType: 'TRANSACTION',
    entityId: 'txn-1',
    entityLabel: 'Weekly groceries',
    action: 'UPDATE',
    origin: 'USER',
    changes: { amount: { from: 42.5, to: 50 } },
    requestId: 'req-3',
  },
  {
    id: 'audit-2',
    occurredAt: '2026-03-16T12:00:00Z',
    entityType: 'ACCOUNT',
    entityId: 'acct-2',
    entityLabel: 'Old Savings',
    action: 'CLOSE',
    origin: 'USER',
    changes: { closedDate: { from: null, to: '2026-03-16' } },
    requestId: 'req-2',
  },
  {
    id: 'audit-2b',
    occurredAt: '2026-03-16T12:00:00Z',
    entityType: 'RECURRING_TEMPLATE',
    entityId: 'rt-1',
    entityLabel: 'Rent',
    action: 'CLOSE',
    origin: 'SYSTEM',
    changes: { active: { from: true, to: false } },
    requestId: 'req-2',
  },
  {
    id: 'audit-1',
    occurredAt: '2026-03-15T09:15:00Z',
    entityType: 'CATEGORY',
    entityId: 'cat-1',
    entityLabel: 'Groceries',
    action: 'CREATE',
    origin: 'USER',
    changes: { name: { from: null, to: 'Groceries' }, type: { from: null, to: 'EXPENSE' } },
    requestId: 'req-1',
  },
]

/**
 * Default success-path handler for `GET /api/audit-log` (F025's REST API), backed by the seed
 * array above. Filters/paginates the same way the real backend does, newest first.
 */
export const auditLogHandlers = [
  http.get(AUDIT_LOG_URL, ({ request }) => {
    const url = new URL(request.url)
    const from = url.searchParams.get('from')
    const to = url.searchParams.get('to')
    const entityType = url.searchParams.get('entityType')
    const action = url.searchParams.get('action')
    const origin = url.searchParams.get('origin')
    const entityId = url.searchParams.get('entityId')
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')

    const filtered = seedAuditLogEntries
      .filter((e) => !from || e.occurredAt >= from)
      .filter((e) => !to || e.occurredAt <= to)
      .filter((e) => !entityType || e.entityType === entityType)
      .filter((e) => !action || e.action === action)
      .filter((e) => !origin || e.origin === origin)
      .filter((e) => !entityId || e.entityId === entityId)
      .sort((a, b) => (a.occurredAt < b.occurredAt ? 1 : -1))

    const start = page * size
    const content = filtered.slice(start, start + size)

    return HttpResponse.json({
      content,
      page: {
        size,
        number: page,
        totalElements: filtered.length,
        totalPages: Math.max(1, Math.ceil(filtered.length / size)),
      },
    })
  }),
]
