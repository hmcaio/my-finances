// Serves a built frontend and proxies /api to the backend on the same origin, standing in for the
// production nginx: node proxy.mjs <dist dir> <port> <backend port>.
import http from 'node:http'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { extname, join, normalize } from 'node:path'

const [root, port, backendPort] = [
  process.argv[2],
  Number(process.argv[3]),
  Number(process.argv[4]),
]
const types = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.svg': 'image/svg+xml',
  '.json': 'application/json',
  '.woff2': 'font/woff2',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
}
http
  .createServer((req, res) => {
    if (req.url.startsWith('/api/')) {
      const upstream = http.request(
        {
          host: '127.0.0.1',
          port: backendPort,
          path: req.url,
          method: req.method,
          headers: { ...req.headers, host: `127.0.0.1:${port}`, 'x-real-ip': '127.0.0.1' },
        },
        (response) => {
          res.writeHead(response.statusCode, response.headers)
          response.pipe(res)
        },
      )
      upstream.on('error', () => {
        res.writeHead(502)
        res.end()
      })
      req.pipe(upstream)
      return
    }
    let file = join(root, normalize(req.url.split('?')[0]))
    if (!existsSync(file) || statSync(file).isDirectory()) file = join(root, 'index.html')
    res.writeHead(200, {
      'Content-Type': types[extname(file)] ?? 'application/octet-stream',
      'Cache-Control': 'no-store',
    })
    createReadStream(file).pipe(res)
  })
  .listen(port, '127.0.0.1')
