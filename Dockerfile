FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build

ADD . .
RUN ./gradlew shadowJar

###

FROM eclipse-temurin:17-jre-alpine

COPY --from=build /build/build/libs/diskord-bot-1.0.0-all.jar /app/bot.jar
WORKDIR /config

CMD ["java", "-jar", "/app/bot.jar"]
