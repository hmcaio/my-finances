# Responsive layout, tables and dialogs (F021, ADR 0019)

Read when building or changing a list table, a form dialog, a filter row, inline row editing, or any layout that must hold at 360-1600px.

- **No hard-coded, non-shrinking widths on content that must fit a 360px viewport.** A `maxWidth` cap (an outlined section, `Layout`'s `maxWidth: 1600`) is fine: it only ever bounds width from above, and the element still shrinks below that on a small screen. A fixed `width` that doesn't respond to the viewport is the thing to avoid on anything outside a dialog's narrow numeric field.
- In `sx`, a bare number for `width`/`height` is a fraction (`width: 1` = 100%), not a pixel: use `'1px'` in a visually-hidden style or the absolutely positioned span spans the page and causes horizontal overflow.
- **Breakpoint logic lives in `hooks/useBreakpointBand`** (`useBreakpointBand()` -> mobile/tablet/desktop, `useIsMobile()`); feature code uses it or `theme.breakpoints` in `sx`. A raw `useMediaQuery` import is a lint error everywhere except that hook and `Layout`. Unit tests fake the viewport with `setViewportWidth`/`restoreViewport` from `src/test/viewport.ts`. In jsdom without `setViewportWidth`, the band is `tablet`, not desktop: pin the width in tests that need every column.
- **`Layout` switches nav at `lg` (1200px)**: see [frontend-e2e.md](frontend-e2e.md) for the test consequences.

## Components

- `ResponsiveTable` (F021): the list table for pages with 3+ data columns. Declare columns once (`role`: `primary`/`secondary`/`hideOnCard`, `tabletPriority: 'low'` to drop on tablet, where each row gets a chevron expanding the hidden columns as label/value pairs), get a table from `sm` up and cards below it (`renderCard` overrides the generic card); `actions` render in both modes. It wraps `DataTableBody`, so pass the same `state`/`onRetry`. 1-2 column tables stay tables; `embedded` drops the table's outlined frame for lists inside an already-outlined section.
- `ResponsiveDialog` (`feedback/`) for form dialogs (full-screen below `sm`; `FormGrid` for one-column-on-phone fields; `ConfirmDialog` stays as is).
- `ResponsiveFilterBar` (`layout/`) for filter rows (inline from `sm`, "Filters" sheet with count badge below; filters apply live, no Apply step).
- `PaginationControls` goes compact by itself.

## Row editing pattern

Put a row's edit inputs in one component and render it inline in the cell (tablet/desktop) and inside a `ResponsiveDialog` (mobile, from the card's Edit) so both share validation. `TransactionFormFields` (`features/transactions`) is the reference (fields only, the parent supplies layout and buttons; long fields carry `gridColumn: '1 / -1'` for the dialog's `FormGrid`), and `ResponsiveTable.test.tsx` has a minimal example.

- Where a page has a separate add/edit form panel rather than true inline row editing (Transactions, Transfers, the Accounts add form), drop the panel and use the dialog at every size so there is one surface and labels never duplicate. A form that owns its state and buttons, like `AccountCreateForm`/`TransferForm`, takes a `dialog` prop that lays it out as `DialogContent` + `FormGrid` + `DialogActions`, so it still serves the inline spots that use it. Show a save error inside the open dialog (the page banner is behind it).
- A mobile edit _dialog_ for true inline editing is only worth adding when the row's own presentation actually changes on mobile (a table that becomes cards, e.g. `BudgetsPage`/`RecurringTemplatesPage`). A page whose table stays a plain table at every size per the 1-2-column rule (`CategoriesPage`, `PaymentMethodsPage`, `InstitutionsPage`, and the categories level of `InvestmentCategoriesPage`) keeps its inline rename inline everywhere too, since the same `<TableCell>`-scoped field is equally usable at 390px and 1280px and a dialog would just be a second surface for no benefit.
