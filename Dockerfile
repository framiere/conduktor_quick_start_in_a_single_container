FROM ubuntu:24.04
 
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates curl gosu tzdata iproute2 jq \
     postgresql-16 postgresql-client-16 htop kafkacat openssl netcat-openbsd \
  && rm -rf /var/lib/apt/lists/* \
  && curl -sSL https://github.com/mikefarah/yq/releases/download/v4.44.3/yq_linux_amd64 -o /usr/local/bin/yq \
  && chmod +x /usr/local/bin/yq \
  && curl -sSL -o /usr/local/bin/conduktor https://github.com/conduktor/ctl/releases/latest/download/conduktor-linux-amd64 \
  && chmod +x /usr/local/bin/conduktor

# Conduktor Console (UI) + Java runtime
COPY --from=conduktor/conduktor-console:1.40.0 /opt/conduktor /opt/conduktor
COPY --from=conduktor/conduktor-console:1.40.0 /opt/console /opt/console
COPY --from=conduktor/conduktor-console:1.40.0 /opt/java /opt/java
COPY --from=conduktor/conduktor-console:1.40.0 /var/www /var/www

# Gateway app jar
COPY --from=conduktor/conduktor-gateway:3.15.0 /app /opt/gateway-app

# Monitoring (Cortex/Prometheus bundle)
COPY --from=conduktor/conduktor-console-cortex:1.40.0 /opt/monitoring /opt/monitoring

# Data generator jar
COPY --from=conduktor/conduktor-data-generator:0.9 /app /opt/datagen-app

# Redpanda binaries
COPY --from=docker.redpanda.com/redpandadata/redpanda:v24.1.6 /usr/bin/redpanda /usr/bin/redpanda
COPY --from=docker.redpanda.com/redpandadata/redpanda:v24.1.6 /usr/bin/rpk /usr/bin/rpk
COPY --from=docker.redpanda.com/redpandadata/redpanda:v24.1.6 /etc/redpanda /etc/redpanda
COPY --from=docker.redpanda.com/redpandadata/redpanda:v24.1.6 /opt/redpanda /opt/redpanda
COPY redpanda.yaml /etc/redpanda/redpanda.yaml

RUN mkdir -p /var/lib/conduktor/pg1 /var/lib/conduktor/pg2 /var/lib/conduktor/redpanda /var/lib/conduktor/certs /var/log/conduktor

COPY entrypoint.sh /entrypoint.sh
COPY certs.sh /opt/conduktor/certs.sh
RUN chmod +x /entrypoint.sh /opt/conduktor/certs.sh

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="$JAVA_HOME/bin:$PATH"

EXPOSE 8080 8888 6969 8081 18081 18082 19092 19644

ENTRYPOINT ["/entrypoint.sh"]
