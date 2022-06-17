FROM adoptopenjdk/openjdk11:jre-11.0.10_9-alpine
ARG JAR_FILE=target/amazon-keyspaces-with-apache-kafka-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} amazon-keyspaces-with-apache-kafka.jar

ENTRYPOINT ["java","-jar","-Xmx2g","-Xms2g","/amazon-keyspaces-with-apache-kafka.jar"]