# Stage 1: build the JAR (needs JDK, Maven and your source code).
# --platform=$BUILDPLATFORM pins this stage to the machine doing the build, so a
# multi-architecture build compiles once natively instead of once per architecture
# under emulation. Java bytecode is the same everywhere, so the JAR is portable.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY . .
RUN mvn package -DskipTests

# Stage 2: run the JAR (JRE is enough)
FROM eclipse-temurin:25-jre
# OpenTelemetry Java agent, enabled through JAVA_TOOL_OPTIONS in docker-compose.prod.yml.
ADD --chmod=644 --checksum=sha256:f787eb6c7f3d18e69a431e108a15278d25ee37f83d68b678f621e063f3988f82 \
    https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.32.0/opentelemetry-javaagent-2.32.0.jar \
    /opt/opentelemetry-javaagent.jar
USER 1000
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_DEFAULT=prod
CMD ["java", "-jar", "app.jar"]
