import { it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse, type JsonBodyType } from 'msw'
import { server } from '../mocks/server'

/** Config for {@link expectLoadStates}. */
export interface LoadStatesConfig {
  /**
   * Renders the page/component under test, e.g. `() => render(<PaymentMethodsPage />)` or
   * `() => renderWithRouter(<AccountsPage />)`. Called fresh for each of the two generated cases.
   */
  render: () => void
  /**
   * The endpoint to slow down / fail. Any endpoint the page's `useAsyncData`/`usePagedData`
   * (directly, or composed through `combineLoadState`) depends on for its first render works,
   * since `combineLoadState` stays loading until every source resolves and surfaces the first
   * failure - it doesn't have to be "the" primary fetch. A page that embeds a prop-less widget
   * with its own overlapping data (`RecurringTemplatesPage` + `PendingOccurrencesWidget`) may show
   * the skeleton/failure marker in more than one place at once; the assertions below tolerate 1..N
   * matches for exactly that reason.
   */
  url: string
  /** The JSON body the intercepted endpoint returns once it succeeds (used for the delayed-success
   * case; the after-retry success reuses the suite's already-registered default handler). */
  successBody: JsonBodyType
  /** Text proving the real content rendered once loading finished - a seeded row's name/label is
   * usually enough. May legitimately appear more than once (e.g. `RecurringTemplatesPage` and its
   * embedded `PendingOccurrencesWidget` can both show the same template description). */
  loadedText: string | RegExp
}

/**
 * Adds the two load-state cases every list page should have (F015 audit's F2): a slow first load
 * shows the delayed skeleton then the real content, and a failed first load shows "Could not load
 * data" + Retry, with Retry re-fetching successfully. Call inside an existing `describe(...)`
 * block alongside a page's other `it()`s, same as `findRow`/`selectOption` from `testUtils`.
 */
export function expectLoadStates({
  render: renderPage,
  url,
  successBody,
  loadedText,
}: LoadStatesConfig): void {
  it('shows a loading skeleton only when the first fetch is slow, then loads', async () => {
    // Real 400ms MSW delay on purpose: it must outlast useDelayedFlag's 150ms gate while
    // findBy* polls. Fake timers would stall RTL's waitFor and MSW's own delay() (flaky).
    server.use(
      http.get(url, async () => {
        await delay(400)
        return HttpResponse.json(successBody)
      }),
    )
    renderPage()

    expect((await screen.findAllByText('Loading…')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText(loadedText)).length).toBeGreaterThan(0)
    expect(screen.queryAllByText('Loading…')).toHaveLength(0)
  })

  it('shows a "Could not load data" notice on failure, and Retry reloads successfully', async () => {
    server.use(http.get(url, () => new HttpResponse(null, { status: 500 }), { once: true }))
    const user = userEvent.setup()
    renderPage()

    expect((await screen.findAllByText(/Could not load data/)).length).toBeGreaterThan(0)

    for (const button of screen.getAllByRole('button', { name: 'Retry' })) {
      await user.click(button)
    }

    expect((await screen.findAllByText(loadedText)).length).toBeGreaterThan(0)
    expect(screen.queryAllByText(/Could not load data/)).toHaveLength(0)
  })
}
