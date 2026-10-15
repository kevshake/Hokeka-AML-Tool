# Multi-stage build: Node.js (repo root context for design-tokens) → nginx serve
FROM node:20-alpine AS builder

WORKDIR /app/FRONTEND

COPY FRONTEND/package*.json ./
RUN npm ci

# Shared design tokens live beside FRONTEND/; globals.css imports ../../../design-tokens/tokens.css
COPY design-tokens /app/design-tokens
COPY FRONTEND/ .

RUN npm run build:prod

FROM nginx:1.25-alpine

COPY FRONTEND/nginx.prod.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/FRONTEND/dist /usr/share/nginx/html

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/health || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
