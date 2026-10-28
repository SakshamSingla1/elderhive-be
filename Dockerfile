# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests

# ---- Run stage ----
# A plain JRE is enough here (unlike ~/DSA_Java/backend's own Dockerfile, which needs the
# full JDK plus g++/python3/node for its code-runner feature) - this app never compiles or
# shells out to anything at runtime.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
