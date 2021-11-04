FROM openjdk:17-jdk-slim-buster AS build
WORKDIR /build

ADD . .
RUN ./gradlew shadowJar

###

FROM openjdk:17-jdk-slim-buster

COPY --from=build /build/build/libs/diskord-bot-1.0.0-all.jar /app/bot.jar
WORKDIR /config

CMD ["java", "-jar", "/app/bot.jar"]
