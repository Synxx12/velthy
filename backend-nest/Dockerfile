# Build with the full toolchain, then run the compiled output on a slim image.
#
# Two stages on purpose. The build stage needs TypeScript, the Nest CLI and every
# dev dependency; the runtime stage needs the compiled `dist/`, production
# dependencies, and nothing else. That is the difference between a ~500 MB image
# and a ~180 MB one, and it is the reason `npm ci` runs twice with different
# flags rather than once.
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
# `npm ci` rather than `npm install`: the lockfile is the contract, and a build
# that quietly resolves a different tree than the one that was tested is not a
# reproducible build.
RUN npm ci

COPY tsconfig.json nest-cli.json ./
COPY src ./src
RUN npm run build

# --- runtime -------------------------------------------------------------
FROM node:22-alpine AS runtime

ENV NODE_ENV=production
WORKDIR /app

# Only what production needs.
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY --from=build /app/dist ./dist
COPY startup.mjs ./

# The host (and Pterodactyl) decides the port through PORT/SERVER_PORT; 8080 is
# only the fallback the server itself uses when neither is set.
EXPOSE 8080

# Not root. The server writes nothing to disk, so it never needs to be.
USER node

# The same entry point used everywhere: it prefers dist/ and says so in the log.
CMD ["node", "startup.mjs"]
