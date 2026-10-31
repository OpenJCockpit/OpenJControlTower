FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package
FROM eclipse-temurin:25-jre-alpine
# git: clone workspaces (Kubernetes init container) and let operators commit/push generated specs.
# Numeric UID/GID so Kubernetes can enforce runAsNonRoot.
RUN apk add --no-cache git && addgroup -S -g 10001 tower && adduser -S -u 10001 -G tower tower
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/data /workspace/repos && chown -R 10001:10001 /app /workspace/repos
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
