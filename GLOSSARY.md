# My Finances

A single-user finance tracker: income and expenses against named accounts, monthly category budgets, recurring bills, manually valued investments and net worth. This file fixes the vocabulary; behaviour and rules live in [`docs/PRD.md`](docs/PRD.md).

## Language

### Money movement

**Transaction**:
An income or an expense in one category, posted to one account. A transfer is not a transaction.
_Avoid_: Entry, payment, purchase

**Transfer**:
Money moving between two accounts the user owns. It is not categorized, never counts toward a budget and does not change net worth.
_Avoid_: Payment (including "card payment"), movement

**Credit card payment**:
A transfer from an asset account to a credit card account. Paying the statement is never an expense.

**Trade confirmation**:
A transfer that buys or sells investments: one cash account, one investment account, one or more product lines and one shared taxes figure. It is the only way money enters or leaves an investment account.
_Avoid_: Trade, order, investment transaction
_UI (pt-BR)_: Confirmação de negociação

**Ledger**:
The unified, month-scoped list of transactions and transfers together.
_UI (pt-BR)_: Lançamentos

**Payment method**:
The rail a transaction used (Debit Card, Credit Card, PIX, Cash). Informational only; the account is what holds the money.
_Avoid_: Account

### Accounts

**Account**:
A named pot of money or debt: checking, savings, cash wallet, credit card or investment.
_Avoid_: Wallet, bank

**Institution**:
A label grouping accounts by where they are held (Nubank, XP). The built-in "No institution" covers money held nowhere.
_Avoid_: Bank, broker

**Opening balance**:
The value of a non-investment account when it was created. Investment accounts have none.
_Avoid_: Initial balance, starting balance

**Closed account**:
An account that accepts no new activity but keeps its history and its past net worth. Closing is what an account with history gets instead of deletion.

**Net worth**:
Asset account balances plus investment values minus credit card amounts owed, as of a date. Computed, never stored.
_UI (pt-BR)_: Patrimônio líquido

### Budgets and recurring bills

**Budget**:
A monthly spending cap on one expense category.
_Avoid_: Limit, envelope

**Version**:
One effective-from state of a budget, recurring template or allocation plan. Editing adds a version going forward; earlier versions are never changed.
_Avoid_: Revision, edit

**Stopped budget**:
A budget with no cap from a month onward (a version with no cap). It can be resumed later; the months in between stay unbudgeted.
_Avoid_: Deleted budget
_UI (pt-BR)_: Interrompido

**Recurring template**:
A versioned definition of a repeating bill or income (category, account, amount, day of month).
_Avoid_: Schedule, subscription, standing order
_UI (pt-BR)_: Modelo recorrente

**Pending occurrence**:
A dated instance a recurring template generated that the user has not yet confirmed. Confirming it creates a transaction.
_Avoid_: Scheduled transaction, upcoming bill

**Catch-up**:
Generating a pending occurrence for every missed cycle when the app next runs. There is no scheduler.

### Categories

**Category**:
The label of a transaction, either income or expense. Distinct from an investment category.
_Avoid_: Tag

**Protected category**:
A category the user can neither rename nor delete: the two "Other" fallbacks, the fuel category and the dividend category.
_Avoid_: System category, built-in category (built-in names only the two fallbacks)

### Investments

**Investment category** / **Investment sub-category**:
The fixed two-level taxonomy of an investment product (Variable Income → REITs (FIIs)). Not the same as a transaction category.
_Avoid_: Asset class, category (unqualified)

**Investment product**:
A specific instrument, such as PETR4 or "Tesouro Selic 2029", identified once across every account that holds it.
_Avoid_: Asset, security, investment, ticker
_UI (pt-BR)_: Produto

**Holding**:
An investment product as held in one investment account; the same product at two brokers is two holdings.
_Avoid_: Position, investment account, asset
_UI (pt-BR)_: Posição

**Snapshot**:
A manually entered value of a holding on a date; the latest one is the holding's current value. Nothing is valued automatically.
_Avoid_: Valuation, balance (unqualified), price
_UI (pt-BR)_: Registro

**Needs snapshot**:
A holding whose latest buy, sell or opening position is newer than its latest snapshot, so its shown value may be stale.
_UI (pt-BR)_: Precisa de registro

**Opening position**:
A holding's starting quantity and average price, recorded without moving any cash. Not an opening balance.
_Avoid_: Initial position, opening balance
_UI (pt-BR)_: Posição inicial

**Cota**:
One unit of a fund-like product, mostly an FII. The unit used for quantity held.
_Avoid_: Share, unit

**Contributed**:
Net cash put into a product: buys minus sells, plus an opening position's cost. Not a return.
_Avoid_: Invested, cost basis

**Split**:
A corporate action that changes how many cotas a product has, recorded once per product by ratio. A reverse split is a grouping.
_Avoid_: Desdobramento (use only for the Portuguese UI)

**Closed holding**:
A holding that is no longer used; only possible once its latest snapshot is zero or absent.

### Allocation and goals

**FII**:
A Brazilian real-estate investment fund, held as a product under REITs (FIIs).

**Segment**:
The kind of real estate an FII holds (Logística, Shoppings), from a fixed list. Unrelated to the investment category.
_Avoid_: Sector

**Allocation plan**:
The versioned target percentages across FII products. Compared against the actual share by value; it suggests no amounts.
_Avoid_: Target, rebalance, portfolio plan

**Goal**:
Something to save toward, with a target amount, an optional date and a planned monthly contribution.
_Avoid_: Objective, target (the amount is the "target amount")
_UI (pt-BR)_: Meta

**Goal allocation**:
The percentage of an investment product's value assigned to a goal. Unrelated to an allocation plan.
_Avoid_: Allocation (unqualified), link

**Projection**:
An estimate of a goal's future value from typed assumptions (planned contribution and three real annual returns), in today's money.
_Avoid_: Forecast, prediction

### Lifecycle verbs

Each ending has its own word; do not mix them.

**Close**:
Ends an account or a holding while keeping its history.
_Avoid_: Archive, deactivate, delete

**Deactivate**:
Stops a recurring template from generating occurrences; reversible.
_Avoid_: Close, stop

**Archive**:
Takes a goal out of use, freeing its allocations. Set by the user, never automatic.
_Avoid_: Close

**Delete**:
Removes a row permanently, allowed only where nothing references it.

### Other

**Fuel details**:
Vehicle, fuel type, liters and price on a transaction in the fuel category.
_Avoid_: Fuel log

**Vehicle**:
A label for grouping fuel purchases. It is not a valued asset.

**Activity log entry**:
An append-only record of one committed change, written in the same transaction as the change. The domain term is audit log entry.
_Avoid_: History (that word belongs to versions and snapshots)
_UI (pt-BR)_: Atividade
