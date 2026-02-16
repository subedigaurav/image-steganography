## Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /build

# Resolve dependencies first (cached unless pom.xml changes)
COPY pom.xml .
RUN mvn dependency:resolve -B -q

# Build application
COPY src ./src
RUN mvn package -DskipTests -B -q

## Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 4000

# Production profile + container-aware JVM
ENV SPRING_PROFILES_ACTIVE=production
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
