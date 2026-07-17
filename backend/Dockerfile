FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ticketflow && adduser -S ticketflow -G ticketflow
WORKDIR /app
COPY --from=build /workspace/target/ticketing-*.jar app.jar
USER ticketflow
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
