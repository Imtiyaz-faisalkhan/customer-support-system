# Render deployment image (deploy branch): the React build is served by the Spring Boot app, same origin as /api.
# Local development doesn't use this (see CLAUDE.md); docs/DEPLOY_RENDER.md explains the deployment.

# 1. Frontend -> static files
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Backend jar, with the frontend build inside as static resources
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app/backend
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /app/frontend/dist src/main/resources/static
RUN ./mvnw -q package -DskipTests

# 3. Runtime: JRE only. Render's free instance has 512 MB and 0.1 CPU: cap the heap below the container limit,
# use the low-overhead serial GC, and the C1-only JIT for a faster start.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/backend/target/backend-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxMetaspaceSize=160m"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
