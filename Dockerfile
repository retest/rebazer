FROM openjdk:8-jdk-alpine

VOLUME /rebazer-workspace
RUN touch /application.yml

ARG JAR_FILE
ADD ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "/app.jar"]
