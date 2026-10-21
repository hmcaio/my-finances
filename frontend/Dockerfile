# Production image for the frontend (F014). Not used in local dev (F001 runs the frontend
# natively via `npm run dev`) — see ADR 0006. Built by CI on `vX.Y.Z` tags (and
# `vX.Y.Z-rc.N`, ADR 0032) only; can also be built locally for the docker-compose.prod.yml
# smoke test:
#   docker build -t my-finances-frontend:local frontend
FROM node:24.21.0-alpine AS build
WORKDIR /app

COPY package.json package-lock.json .npmrc ./
RUN npm ci

COPY . .
# .env.production supplies VITE_API_BASE_URL; Vite picks it up automatically for a production
# build (`vite build` defaults to --mode production) and inlines it at build time.
RUN npm run build

# Runtime stage: nginx serving the static build output only, no Node in the final image. The
# -alpine-slim variant omits the full image's extra modules and their libraries (curl, libpng,
# libxml2, ...), which nothing here uses and which carried most of the scan findings (issue #174).
FROM nginx:1.30.6-alpine-slim AS runtime

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
