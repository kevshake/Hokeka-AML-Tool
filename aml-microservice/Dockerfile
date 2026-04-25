FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/aml-microservice-1.0.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Ddl4j.enabled=false -Xmx256m"
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
