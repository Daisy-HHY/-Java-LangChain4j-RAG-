# syntax=docker/dockerfile:1
FROM node:22.22.0-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/kgqa-frontend
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    kgqa-frontend true
COPY . .
EXPOSE 5173
ENTRYPOINT ["kgqa-frontend"]
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
