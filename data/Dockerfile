# Compile on the build machine: the JAR is the same for every architecture.
FROM --platform=$BUILDPLATFORM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src

RUN ./mvnw -DskipTests package

FROM eclipse-temurin:25-jre
# OpenTelemetry Java agent, enabled through JAVA_TOOL_OPTIONS in docker-compose.prod.yml.
ADD --chmod=644 --checksum=sha256:f787eb6c7f3d18e69a431e108a15278d25ee37f83d68b678f621e063f3988f82 \
    https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.32.0/opentelemetry-javaagent-2.32.0.jar \
    /opt/opentelemetry-javaagent.jar
USER 1000

WORKDIR /app
COPY --from=build /workspace/target/data-0.1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
