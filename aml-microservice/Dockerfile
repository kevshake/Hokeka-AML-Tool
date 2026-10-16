# Multi-stage build: install vendored aeroorm-java, package aml-microservice, run on JRE 25.
FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /build

COPY aeroorm-java ./aeroorm-java
RUN mvn -B -q -f aeroorm-java/pom.xml install -DskipTests

COPY aml-microservice/pom.xml ./aml-microservice/pom.xml
RUN mvn -B -q -f aml-microservice/pom.xml dependency:go-offline

COPY aml-microservice/src ./aml-microservice/src
RUN mvn -B -q -f aml-microservice/pom.xml package -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=builder /build/aml-microservice/target/aml-microservice-1.0.0.jar app.jar

ENV JAVA_TOOL_OPTIONS="-Ddl4j.enabled=false -Xmx256m"

EXPOSE 8091

ENTRYPOINT ["java", "-jar", "app.jar"]
