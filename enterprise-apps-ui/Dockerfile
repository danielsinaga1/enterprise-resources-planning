# ========== BUILD STAGE ==========
FROM node:20-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Build with empty API base URLs so the bundle uses same-origin relative requests;
# nginx proxies /api and /uploads to the gateway. Optional keys can be injected
# at build time via --build-arg if ever needed.
ARG VITE_API_URL=""
ARG VITE_LIMS_API_URL=""
ARG VITE_FIREBASE_VAPID_KEY=""
ENV VITE_API_URL=$VITE_API_URL \
    VITE_LIMS_API_URL=$VITE_LIMS_API_URL \
    VITE_FIREBASE_VAPID_KEY=$VITE_FIREBASE_VAPID_KEY
RUN npm run build

# ========== RUN STAGE ==========
FROM nginx:1.27-alpine
ENV TZ=Asia/Jakarta

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1

EXPOSE 80
