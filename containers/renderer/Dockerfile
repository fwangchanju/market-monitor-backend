FROM mcr.microsoft.com/playwright:v1.62.0-focal
WORKDIR /app
COPY containers/renderer/package.json containers/renderer/package-lock.json ./
RUN npm ci
COPY containers/renderer/server.js ./
EXPOSE 3000
CMD ["node", "server.js"]
