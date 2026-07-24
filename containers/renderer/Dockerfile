FROM mcr.microsoft.com/playwright:v1.40.0-focal
WORKDIR /app
COPY containers/renderer/package.json ./
RUN npm install
COPY containers/renderer/server.js ./
EXPOSE 3000
CMD ["node", "server.js"]
