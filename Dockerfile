FROM maven:3.9.11-eclipse-temurin-21-alpine AS construtor
WORKDIR /projeto
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S aplicacao && adduser -S -G aplicacao aplicacao
WORKDIR /aplicacao
COPY --from=construtor /projeto/target/desafio-ativacao-0.0.1-SNAPSHOT.jar aplicacao.jar
USER aplicacao
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "aplicacao.jar"]
