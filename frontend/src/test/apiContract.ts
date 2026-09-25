import { describe, expect, it } from 'vitest'
import type { RequestHandler } from 'msw'
import { server } from '../mocks/server'
import { ApiError } from '../api/core/apiError'

/**
 * Config for {@link describeNamedEntityApi}: the shared shape behind every flat-taxonomy API
 * client (`categories`, `paymentMethods`, `institutions`, `investmentCategories`,
 * `investmentSubcategories` — see `frontend/CLAUDE.md`'s free-text-fields convention). `get` is
 * optional because `investmentSubcategories` has no standalone list endpoint: sub-categories are
 * only ever read nested under their category (`investmentCategories.ts`).
 */
export interface NamedEntityApiContract<Entity, CreateRequest, RenameRequest> {
  /** Used in the outer `describe` title, e.g. `'categories'` -> `'categories API client'`. */
  label: string
  api: {
    get?: () => Promise<Entity[]>
    create: (request: CreateRequest) => Promise<Entity>
    rename: (id: string, request: RenameRequest) => Promise<Entity>
    remove: (id: string) => Promise<void>
  }
  /** Required together with `api.get`: the exact list `get()` should resolve to. */
  seedList?: Entity[]
  create: {
    request: CreateRequest
    /** Asserted with `toMatchObject` against the created entity - a plain object of expected
     * values, or `expect.any(String)`-style asymmetric matchers (hence `unknown`, not `Entity`). */
    expect: Partial<Record<keyof Entity, unknown>>
  }
  rename: {
    id: string
    request: RenameRequest
    /** Asserted with `toMatchObject` against the renamed entity; see `create.expect`. */
    expect: Partial<Record<keyof Entity, unknown>>
  }
  remove: {
    id: string
  }
  conflict: {
    /** The delete-conflict message (`CONFLICT_MESSAGE` in the entity's api client). */
    message: string
    /** The duplicate-name message (`DUPLICATE_NAME_MESSAGE` in the entity's api client). */
    duplicateNameMessage: string
    createHandler: RequestHandler
    renameHandler: RequestHandler
    deleteHandler: RequestHandler
    /** Request bodies for the conflict paths - often the same values as the happy-path ones, but
     * kept separate since a rename-conflict needs an id distinct from the happy-path rename. */
    createRequest: CreateRequest
    renameId: string
    renameRequest: RenameRequest
  }
}

/**
 * Runs the case set every flat-taxonomy API client test file shares (get, create, rename, delete,
 * and the three 409 mappings) against the given config, so a client that's missing one of them
 * (F015 audit's F5: duplicate-name-on-add wasn't tested for `categories`/`paymentMethods`) gets it
 * automatically instead of by manually copying one more `it()`.
 */
export function describeNamedEntityApi<Entity, CreateRequest, RenameRequest>(
  config: NamedEntityApiContract<Entity, CreateRequest, RenameRequest>,
): void {
  const { label, api, seedList, create, rename, remove, conflict } = config

  describe(`${label} API client`, () => {
    if (api.get) {
      it('returns the seeded list', async () => {
        await expect(api.get!()).resolves.toEqual(seedList)
      })
    }

    it('creates and returns the created entity', async () => {
      const created = await api.create(create.request)
      expect(created).toMatchObject(create.expect)
    })

    it('renames and returns the updated entity', async () => {
      const updated = await api.rename(rename.id, rename.request)
      expect(updated).toMatchObject(rename.expect)
    })

    it('deletes successfully', async () => {
      await expect(api.remove(remove.id)).resolves.toBeUndefined()
    })

    it('maps a 409 to the delete-conflict message', async () => {
      server.use(conflict.deleteHandler)

      const error: unknown = await api.remove(remove.id).catch((err: unknown) => err)

      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(409)
      expect((error as ApiError).message).toBe(conflict.message)
    })

    it('maps a 409 to the duplicate-name message on create', async () => {
      server.use(conflict.createHandler)

      const error: unknown = await api.create(conflict.createRequest).catch((err: unknown) => err)

      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(409)
      expect((error as ApiError).message).toBe(conflict.duplicateNameMessage)
    })

    it('maps a 409 to the duplicate-name message on rename', async () => {
      server.use(conflict.renameHandler)

      const error: unknown = await api
        .rename(conflict.renameId, conflict.renameRequest)
        .catch((err: unknown) => err)

      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(409)
      expect((error as ApiError).message).toBe(conflict.duplicateNameMessage)
    })
  })
}
