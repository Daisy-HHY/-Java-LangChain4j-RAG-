# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# Optional secrets support builds behind a trusted HTTPS proxy without baking
# proxy settings or certificates into the image. Normal local builds need none.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=proxy_ca \
    --mount=type=secret,id=maven_settings \
    set -eu; \
    if [ -f /run/secrets/proxy_ca ]; then \
      cp "$JAVA_HOME/lib/security/cacerts" /tmp/maven-cacerts; \
      keytool -importcert -noprompt -alias build-proxy -file /run/secrets/proxy_ca \
        -keystore /tmp/maven-cacerts -storepass changeit; \
      export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/tmp/maven-cacerts"; \
    fi; \
    set --; \
    if [ -f /run/secrets/maven_settings ]; then set -- -s /run/secrets/maven_settings; fi; \
    mvn "$@" -B -ntp package -DskipTests; \
    rm -f /tmp/maven-cacerts

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/rag-qa-system-1.0.0.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
