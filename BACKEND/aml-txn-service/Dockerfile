FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/aml-txn-service-*.jar /app/app.jar
EXPOSE 2637
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:2637/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
