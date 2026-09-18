# Java 25 (LTS) JRE — enables virtual threads (spring.threads.virtual.enabled) with no
# synchronized pinning (JEP 491). Aerospike lookups are blocking I/O, the ideal VT workload.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/aml-microservice-1.0.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Ddl4j.enabled=false -Xmx256m"
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
