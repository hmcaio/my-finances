// The "how many SQL statements" half: counts what Postgres runs for ONE request, to show a read
// does not grow with the ledger. See docs/conventions/performance.md for why it sets the logging
// per database and reconnects the pool.
import { config, dates, postgresLog, psql, signIn } from './lib.mjs'

const { api, username, password } = config()
const call = await signIn(api, username, password)
const { today, fiveYearsAgo } = dates()
const cases = {
  'accounts list': '/api/accounts',
  'net worth now': '/api/net-worth',
  'trend CHANGE_DATE 5y': `/api/net-worth/trend?granularity=CHANGE_DATE&from=${fiveYearsAgo}&to=${today}`,
}
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

try {
  // A `-c log_min_duration_statement` on the server's command line beats ALTER SYSTEM, but a
  // per-database setting beats the command line - for NEW sessions, so the pool must reconnect.
  psql('alter database myfinances set log_min_duration_statement = 0')
  psql(
    "select count(pg_terminate_backend(pid)) from pg_stat_activity where datname = 'myfinances' and pid <> pg_backend_pid()",
  )
  await pause(1500)
  await call('GET', '/api/accounts') // reopens the pool and warms it
  await pause(500)
  for (const [name, path] of Object.entries(cases)) {
    const before = postgresLog().split('\n').length
    const { status } = await call('GET', path)
    await pause(300)
    const statements = postgresLog()
      .split('\n')
      .slice(before)
      .filter((line) => /LOG:\s+duration:/.test(line)).length
    console.log(`${name.padEnd(24)} status ${status}  SQL statements: ${statements}`)
  }
} finally {
  psql('alter database myfinances reset log_min_duration_statement')
}
