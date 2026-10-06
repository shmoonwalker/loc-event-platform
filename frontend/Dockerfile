FROM node:24-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
# Rendered into /etc/nginx/conf.d/default.conf at start-up (official image feature).
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
# BACKEND_URL: where /api is forwarded. Override per environment (on Railway: the backend's private URL).
ENV BACKEND_URL=http://backend:8080
# Fill ${NGINX_LOCAL_RESOLVERS} from /etc/resolv.conf for the lazy upstream lookup.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
# Only substitute our own variables; nginx's $uri etc. must stay untouched.
ENV NGINX_ENVSUBST_FILTER=^(BACKEND_URL|NGINX_LOCAL_RESOLVERS)$
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 3000