# F020 — Playwright E2E Tooling (viewport matrix, mocked API)

## Summary
Adds Playwright to the frontend so layout can be verified in a real browser at mobile, tablet and desktop widths. jsdom (Vitest) does not compute layout, so it cannot catch horizontal overflow or the wrong nav/table mode. This feature builds the tooling and a smoke suite only; [F021](../F021-responsive-layout/spec.md) uses it. Frontend only: no backend, API or data-model change.

## Scope
- Playwright install, config, `npm run e2e` script.
- Viewport project matrix, shared geometry/DOM assertion helpers.
- API mocking through Playwright `route` interception, reusing the `src/mocks` fixtures.
- One smoke spec per viewport project.
- A CI job (`e2e`) in `.github/workflows/ci.yml`.
- `frontend/CLAUDE.md`, CHANGELOG (none needed if not user-visible, see Docs).
- Out of scope: see [Non-goals](#non-goals).

## Decisions

| Decision | Why |
|---|---|
| Playwright runs against the Vite dev server (`webServer` in `playwright.config.ts`), with every `/api` request fulfilled by `page.route`. No backend, no Postgres. | Fast and deterministic in CI, no compose stack or seed data to maintain. The app's own contract with the backend is already covered by backend integration tests and MSW tests. |
| Route handlers reuse the fixtures in `src/mocks` (a small adapter turns the MSW handler data into `route.fulfill` responses). MSW itself is not started in the browser. | One source of fixture data. Avoids shipping MSW's service worker into the dev bundle. |
| Viewport projects: `mobile` 390x844, `tablet` 768x1024, `desktop` 1280x800, all Chromium. | Matches the MUI breakpoint bands used by F021 (below 600, 600–1199, 1200 and up). Chromium only keeps CI time low; cross-browser is a non-goal. |
| Assertions are DOM and geometry only. No screenshot baselines, no `toHaveScreenshot`. | Pixel baselines are flaky across Windows and Linux (fonts, anti-aliasing) and add review noise. Geometry checks are stable and test what matters. Screenshots may be attached to CI artifacts for humans but are never compared. |
| Shared helpers in `e2e/support/`: `expectNoHorizontalOverflow(page)` (`document.scrollingElement.scrollWidth <= window.innerWidth`), `expectNavMode(page, 'permanent' \| 'temporary')`, `mockApi(page, overrides?)`. | F021 pages reuse them; each spec stays a few lines. |
| Smoke spec per project: app loads, `Layout` renders, no horizontal overflow, no console errors and no unhandled failed requests (an unmocked `/api` call fails the test). Built as two tests; the overflow one is marked `test.fail` on `mobile` and `tablet` because the dashboard genuinely overflows there until F021, and F021 removes the marker. | Proves the tooling works end to end and that the mock layer is complete for the landing routes. |
| CI: separate `e2e` job in `ci.yml`, `npx playwright install --with-deps chromium` with the browser directory cached, HTML report and traces uploaded as artifacts on failure. | Does not slow the existing frontend job; failure artifacts make layout regressions debuggable. |
| Specs live in `frontend/e2e/`, excluded from Vitest and from the production build. | Keeps the two runners separate. |

## Structure

```
frontend/playwright.config.ts   projects (mobile/tablet/desktop), webServer, reporter
frontend/e2e/support/           mockApi, geometry and nav assertion helpers
frontend/e2e/smoke.spec.ts      per-viewport smoke checks
```

## Testing
- Helpers get a self-check spec against a tiny fixture page (overflow helper fails on a page wider than the viewport, passes otherwise).
- `npm run e2e` green locally on all three projects; `npm run lint && npm test` unaffected.
- CI `e2e` job green on a PR.

## Docs
- `frontend/CLAUDE.md`: how to run e2e, the mock-by-route rule (every `/api` call must be mocked), the geometry-only rule, where helpers live.
- `docs/features/README.md` row.
- Root `README.md` "Project status" entry once built.
- `CHANGELOG.md`: no entry (test/ci tooling, not user-visible).

## Non-goals
- Full-stack e2e against a real backend and Postgres (separate later feature).
- Screenshot or visual-regression baselines.
- Firefox and WebKit projects.
- Real-device or touch-emulation testing beyond viewport size.
- Any backend change.

## Open questions
None.
