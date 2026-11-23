# -------- BUILD STAGE --------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# -------- RUN STAGE --------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
# Fits the 512 MB free plan: heap up to 70% of the container, small thread stacks, a lean garbage collector
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]
