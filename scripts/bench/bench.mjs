// The API half of the #189 benchmark: one warm-up plus five timed calls per endpoint, median and
// max in milliseconds. Prints a line per endpoint. See docs/conventions/performance.md.
import { config, dates, signIn } from './lib.mjs'

const { api, username, password } = config()
const call = await signIn(api, username, password)
const { today, oneYearAgo, fiveYearsAgo } = dates()
const accounts = JSON.parse(
  (await call('GET', '/api/accounts?includeClosed=true')).bytes.toString(),
)
const checking = accounts.find((account) => account.name === 'Bench Checking').id
const all11 = [
  'TRANSACTIONS_TRANSFERS',
  'ACCOUNT_BALANCES',
  'FII_PORTFOLIO',
  'FUEL_HISTORY',
  'BUDGETS_SPENDING',
  'INVESTMENTS',
  'NET_WORTH_TREND',
  'RECURRING_TEMPLATES',
  'INVESTMENT_ALLOCATION',
  'DIVIDEND_INCOME',
  'ANNUAL_SUMMARY',
]
const trend = (granularity, from) =>
  `/api/net-worth/trend?granularity=${granularity}&from=${from}&to=${today}`

const cases = [
  ['accounts list (with balances)', 'GET', '/api/accounts'],
  ['net worth now', 'GET', '/api/net-worth'],
  ['account detail', 'GET', `/api/accounts/${checking}`],
  ['net-worth trend, MONTH, 12 months', 'GET', trend('MONTH', oneYearAgo)],
  ['net-worth trend, CHANGE_DATE, 12 months', 'GET', trend('CHANGE_DATE', oneYearAgo)],
  ['net-worth trend, CHANGE_DATE, 5 years', 'GET', trend('CHANGE_DATE', fiveYearsAgo)],
  ['net-worth trend, MONTH, 5 years', 'GET', trend('MONTH', fiveYearsAgo)],
  ['report account-balances', 'POST', '/api/reports', { types: ['ACCOUNT_BALANCES'] }],
  [
    'report net-worth-trend (CHANGE_DATE 5y)',
    'POST',
    '/api/reports',
    {
      types: ['NET_WORTH_TREND'],
      filter: { dateFrom: fiveYearsAgo, dateTo: today, netWorthGranularity: 'CHANGE_DATE' },
    },
  ],
  [
    'report annual-summary',
    'POST',
    '/api/reports',
    { types: ['ANNUAL_SUMMARY'], filter: { month: `${today.slice(0, 4)}-06` } },
  ],
  ['all 11 reports as a zip', 'POST', '/api/reports', { types: all11 }],
]

for (const [name, method, path, body] of cases) {
  const warmUp = await call(method, path, body)
  if (warmUp.status !== 200) throw new Error(`${method} ${path} answered ${warmUp.status}`)
  const times = []
  for (let i = 0; i < 5; i++) times.push((await call(method, path, body)).ms)
  times.sort((a, b) => a - b)
  console.log(
    `${name.padEnd(42)} median ${times[2].toFixed(0).padStart(5)} ms   max ${times[4].toFixed(0).padStart(5)} ms`,
  )
}
