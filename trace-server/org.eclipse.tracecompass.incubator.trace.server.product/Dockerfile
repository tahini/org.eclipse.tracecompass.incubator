FROM openjdk:jre-slim


COPY target/products/traceserver/linux/gtk/x86_64/trace-compass-server/ /usr/src/myapp

WORKDIR /usr/src/myapp/

EXPOSE 8080

CMD ["./tracecompass-server"]
