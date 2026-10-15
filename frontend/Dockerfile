FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/gymflow/browser /usr/share/nginx/html
