# Multi-stage: Vite marketing site (repo root context for design-tokens) → nginx.
FROM node:22-alpine AS builder

WORKDIR /app/website

COPY website/package*.json ./
RUN npm ci

COPY design-tokens /app/design-tokens
COPY website/ .

RUN npm run build

FROM nginx:1.25-alpine

COPY website/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/website/dist /usr/share/nginx/html

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
