FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY server/app/build/libs/urokplan-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
