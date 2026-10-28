// Sends the same requests to a baseline backend and a new one on the SAME database and compares
// the response bodies byte for byte; exits 1 on a difference. BENCH_BASELINE_API and BENCH_API are
// their origins. A report zip is compared entry by entry, and a PDF with its /CreationDate and
// /ID blanked: the PDF writer stamps the generation time, so even one backend differs from itself
// between two identical requests. See docs/conventions/performance.md.
import { inflateRawSync } from 'node:zlib'
import { config, dates, signIn } from './lib.mjs'

const { api, username, password } = config()
const baselineApi = process.env.BENCH_BASELINE_API
if (!baselineApi) throw new Error('BENCH_BASELINE_API is not set: pass --baseline to run.sh')
const baseline = await signIn(baselineApi, username, password)
const current = await signIn(api, username, password)
const { today, oneYearAgo, fiveYearsAgo } = dates()

/** Entry name -> bytes of a zip (stored or deflated entries; reads the central directory). */
function readZip(buffer) {
  let eocd = buffer.length - 22
  while (eocd >= 0 && buffer.readUInt32LE(eocd) !== 0x06054b50) eocd--
  if (eocd < 0) throw new Error('not a zip file')
  const count = buffer.readUInt16LE(eocd + 10)
  let at = buffer.readUInt32LE(eocd + 16)
  const entries = new Map()
  for (let i = 0; i < count; i++) {
    const method = buffer.readUInt16LE(at + 10)
    const size = buffer.readUInt32LE(at + 20)
    const nameLength = buffer.readUInt16LE(at + 28)
    const extraLength = buffer.readUInt16LE(at + 30)
    const commentLength = buffer.readUInt16LE(at + 32)
    const local = buffer.readUInt32LE(at + 42)
    const name = buffer.toString('utf8', at + 46, at + 46 + nameLength)
    const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28)
    const raw = buffer.subarray(start, start + size)
    entries.set(name, method === 0 ? raw : inflateRawSync(raw))
    at += 46 + nameLength + extraLength + commentLength
  }
  return entries
}

const normalizePdf = (bytes) =>
  Buffer.from(
    bytes
      .toString('latin1')
      .replace(/\/CreationDate\(D:[^)]*\)/g, '/CreationDate()')
      .replace(/\/ID \[<[0-9a-f]+><[0-9a-f]+>\]/g, '/ID []'),
    'latin1',
  )

function sameZip(a, b) {
  const [left, right] = [readZip(a), readZip(b)]
  if ([...left.keys()].join() !== [...right.keys()].join()) return false
  return [...left].every(([name, bytes]) =>
    name.endsWith('.pdf')
      ? normalizePdf(bytes).equals(normalizePdf(right.get(name)))
      : bytes.equals(right.get(name)),
  )
}

const accounts = JSON.parse(
  (await current('GET', '/api/accounts?includeClosed=true')).bytes.toString(),
)
const reportTypes = [
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
const asOfDates = [fiveYearsAgo, oneYearAgo, today]
const year = Number(today.slice(0, 4))
const requests = [
  ['GET', '/api/accounts'],
  ['GET', '/api/accounts?includeClosed=true'],
  ...accounts.map((a) => ['GET', `/api/accounts/${a.id}`]),
  ...accounts.flatMap((a) => asOfDates.map((d) => ['GET', `/api/accounts/${a.id}?asOf=${d}`])),
  ['GET', '/api/net-worth'],
  ...asOfDates.map((d) => ['GET', `/api/net-worth?asOf=${d}`]),
  ...['MONTH', 'CHANGE_DATE'].flatMap((g) =>
    asOfDates
      .slice(0, 2)
      .map((from) => ['GET', `/api/net-worth/trend?granularity=${g}&from=${from}&to=${today}`]),
  ),
  ...reportTypes.map((type) => ['POST', '/api/reports', { types: [type] }]),
  ['POST', '/api/reports', { types: ['ACCOUNT_BALANCES'], filter: { asOfDate: oneYearAgo } }],
  [
    'POST',
    '/api/reports',
    {
      types: ['NET_WORTH_TREND'],
      filter: { dateFrom: fiveYearsAgo, dateTo: today, netWorthGranularity: 'MONTH' },
    },
  ],
  ...[0, 1, 2, 3].map((back) => [
    'POST',
    '/api/reports',
    { types: ['ANNUAL_SUMMARY'], filter: { month: `${year - back}-06` } },
  ]),
]

let identical = 0
const different = []
for (const [method, path, body] of requests) {
  const [old, now] = [await baseline(method, path, body), await current(method, path, body)]
  const same =
    old.status === now.status &&
    (old.bytes.equals(now.bytes) ||
      (now.bytes.subarray(0, 2).toString() === 'PK' && sameZip(old.bytes, now.bytes)))
  if (same) identical++
  else different.push(`${method} ${path}${body ? ' ' + JSON.stringify(body).slice(0, 80) : ''}`)
}
console.log(`${requests.length} requests: ${identical} identical, ${different.length} different`)
for (const line of different) console.log(`  DIFFERENT  ${line}`)
process.exit(different.length ? 1 : 0)
