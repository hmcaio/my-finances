import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { seedAccounts } from '../mocks/handlers/accounts'
import { ApiError } from './apiError'
import { closeAccount, createAccount, editAccount, getAccount, getAccounts } from './accounts'

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

    expect(account).toEqual(seedAccounts[0])
  })

  it('createAccount posts the new account and returns the created one', async () => {
    const created = await createAccount({
      name: 'New Account',
      institution: 'Some Bank',
      type: 'CHECKING',
      openingBalance: 100,
      openingBalanceDate: '2026-01-01',
    })

    expect(created).toMatchObject({ name: 'New Account', type: 'CHECKING', balance: 100 })
    expect(created.id).toBeTruthy()
  })

  it('editAccount patches name/institution and returns the updated account', async () => {
    const updated = await editAccount('acct-1', { name: 'Renamed', institution: 'New Bank' })

    expect(updated).toMatchObject({ id: 'acct-1', name: 'Renamed', institution: 'New Bank' })
  })

  it('closeAccount posts to the close endpoint and returns the closed account', async () => {
    const closed = await closeAccount('acct-1')

    expect(closed).toMatchObject({ id: 'acct-1', closed: true })
    expect(closed.closedDate).toBeTruthy()
  })

  it('closeAccount maps a 409 to an ApiError', async () => {
    server.use(
      http.post('/api/accounts/:id/close', () =>
        HttpResponse.json({ message: 'Account is already closed' }, { status: 409 }),
      ),
    )

    const error: unknown = await closeAccount('acct-1').catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
  })
})
