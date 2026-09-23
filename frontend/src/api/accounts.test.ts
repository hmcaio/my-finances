import { describe, expect, it } from 'vitest'
import { server } from '../mocks/server'
import {
  accountAlreadyClosedConflictHandler,
  accountCreateConflictHandler,
  accountEditConflictHandler,
  seedAccounts,
  seedCheckingAccount,
  seedInvestmentAccount,
} from '../mocks/handlers/accounts'
import { ApiError } from './apiError'
import {
  CLOSE_CONFLICT_MESSAGE,
  DUPLICATE_NAME_MESSAGE,
  closeAccount,
  createAccount,
  editAccount,
  getAccount,
  getAccounts,
} from './accounts'

describe('accounts API client', () => {
  it('getAccounts excludes closed accounts by default', async () => {
    const accounts = await getAccounts()

    expect(accounts).toEqual(seedAccounts.filter((a) => !a.closed))
  })

  it('getAccounts includes closed accounts when requested', async () => {
    const accounts = await getAccounts(true)

    expect(accounts).toEqual(seedAccounts)
  })

  it('getAccount returns a single account by id', async () => {
    const account = await getAccount('acct-1')

    expect(account).toEqual(seedCheckingAccount)
  })

  it('createAccount posts the new account, institution included, and returns the created one', async () => {
    const created = await createAccount({
      name: 'New Account',
      institutionId: 'inst-1',
      type: 'CHECKING',
      openingBalance: 100,
      openingBalanceDate: '2026-01-01',
    })

    expect(created).toMatchObject({
      name: 'New Account',
      institutionId: 'inst-1',
      type: 'CHECKING',
      balance: 100,
    })
    expect(created.id).toBeTruthy()
  })

  it('getAccount returns an INVESTMENT account with null opening fields', async () => {
    const account = await getAccount(seedInvestmentAccount.id)

    expect(account).toMatchObject({
      type: 'INVESTMENT',
      openingBalance: null,
      openingBalanceDate: null,
      balance: 0,
    })
  })

  it('createAccount posts an INVESTMENT account without opening fields', async () => {
    const created = await createAccount({
      name: 'XP Investimentos',
      institutionId: 'inst-1',
      type: 'INVESTMENT',
    })

    expect(created).toMatchObject({
      type: 'INVESTMENT',
      openingBalance: null,
      openingBalanceDate: null,
      balance: 0,
    })
  })

  it('closeAccount maps a 409 to a message that covers the open-products case', async () => {
    server.use(accountAlreadyClosedConflictHandler)

    const error: unknown = await closeAccount(seedInvestmentAccount.id).catch((err: unknown) => err)

    expect((error as ApiError).message).toBe(CLOSE_CONFLICT_MESSAGE)
  })

  it('editAccount patches name and institution and returns the updated account', async () => {
    const updated = await editAccount('acct-1', { name: 'Renamed', institutionId: 'inst-2' })

    expect(updated).toMatchObject({ id: 'acct-1', name: 'Renamed', institutionId: 'inst-2' })
  })

  it('closeAccount posts to the close endpoint and returns the closed account', async () => {
    const closed = await closeAccount('acct-1')

    expect(closed).toMatchObject({ id: 'acct-1', closed: true })
    expect(closed.closedDate).toBeTruthy()
  })

  it('closeAccount maps a 409 to an ApiError', async () => {
    server.use(accountAlreadyClosedConflictHandler)

    const error: unknown = await closeAccount('acct-1').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
  })

  it('createAccount maps a 409 to the duplicate-name message', async () => {
    server.use(accountCreateConflictHandler)

    const error: unknown = await createAccount({
      name: 'Itau Checking',
      institutionId: 'inst-1',
      type: 'CHECKING',
      openingBalance: 0,
      openingBalanceDate: '2026-01-01',
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DUPLICATE_NAME_MESSAGE)
  })

  it('editAccount maps a 409 to the duplicate-name message', async () => {
    server.use(accountEditConflictHandler)

    const error: unknown = await editAccount('acct-2', {
      name: 'Itau Checking',
      institutionId: 'inst-1',
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).message).toBe(DUPLICATE_NAME_MESSAGE)
  })
})
