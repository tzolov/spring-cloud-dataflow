FROM tzolov/openjdk-arm:latest

ENV LANG=C.UTF-8

ARG JAR_FILE

ADD target/${JAR_FILE} spring-cloud-dataflow-server.jar

VOLUME ["/tmp"]

ENTRYPOINT exec java -Djava.security.egd=file:/dev/./urandom -jar /spring-cloud-dataflow-server.jar
