FROM openjdk:21-ea-21-jdk-slim-buster

WORKDIR /app

COPY build/libs/specshield-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9000

CMD ["java", "-jar", "app.jar"]