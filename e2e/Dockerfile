# Keep this tag equal to the @playwright/test version in package.json (Renovate groups them).
FROM mcr.microsoft.com/playwright:v1.55.0-noble
WORKDIR /e2e
COPY package.json ./
RUN npm install --no-audit --no-fund
COPY playwright.config.ts ./
COPY tests ./tests
CMD ["npx", "playwright", "test"]
