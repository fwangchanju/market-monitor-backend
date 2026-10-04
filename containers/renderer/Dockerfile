# 의존성(express, playwright)은 순수 JS라 플랫폼과 무관하다. 빌드 머신 플랫폼에서 한 번만 설치해 arm64 에뮬레이션을 피한다
FROM --platform=$BUILDPLATFORM mcr.microsoft.com/playwright:v1.61.1-jammy AS deps
WORKDIR /app
COPY containers/renderer/package.json containers/renderer/package-lock.json ./
RUN npm ci

FROM mcr.microsoft.com/playwright:v1.61.1-jammy
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY containers/renderer/package.json containers/renderer/package-lock.json ./
COPY containers/renderer/server.js ./
EXPOSE 3000
CMD ["node", "server.js"]
