# syntax=docker/dockerfile:1

################################################################################
# Dependencies and build. Keep the root OpenAPI contract at its original path.
################################################################################
FROM node:24-alpine AS dependencies
WORKDIR /opt/build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --include=dev --no-audit --no-fund

FROM dependencies AS build
WORKDIR /opt/build
COPY openapi.yml .editorconfig ./
COPY frontend ./frontend
WORKDIR /opt/build/frontend
RUN npm run build

################################################################################
# Runtime: static assets and the same-origin API proxy; no Node.js or secrets.
################################################################################
FROM nginx:stable-alpine AS frontend
COPY docker/fuel-calc/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /opt/build/frontend/dist /usr/share/nginx/html

USER nginx
EXPOSE 8080
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
