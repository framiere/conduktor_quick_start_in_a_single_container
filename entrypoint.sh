#!/bin/bash
set -euo pipefail

PGBIN=/usr/lib/postgresql/16/bin
DATA1=/var/lib/conduktor/pg1
DATA2=/var/lib/conduktor/pg2
LOGDIR=/var/log/conduktor
mkdir -p "$LOGDIR"

# ensure postgres user
if ! id -u postgres >/dev/null 2>&1; then
  groupadd -r postgres
  useradd -r -g postgres postgres
fi

init_db() {
  local dir="$1" port="$2"
  if [ ! -f "$dir/PG_VERSION" ]; then
    echo "Initializing Postgres at $dir (port $port)"
    install -d -m 700 -o postgres -g postgres "$dir"
    gosu postgres "$PGBIN/initdb" -D "$dir"
    echo "listen_addresses='*'" >> "$dir/postgresql.conf"
    echo "port=$port" >> "$dir/postgresql.conf"
    echo "host all all 0.0.0.0/0 scram-sha-256" >> "$dir/pg_hba.conf"
  fi
}

init_db "$DATA1" 5432
init_db "$DATA2" 5433

# start Postgres instances
gosu postgres "$PGBIN/postgres" -D "$DATA1" -p 5432 >"$LOGDIR/pg1.log" 2>&1 &
gosu postgres "$PGBIN/postgres" -D "$DATA2" -p 5433 >"$LOGDIR/pg2.log" 2>&1 &

for i in $(seq 1 30); do
  "$PGBIN/pg_isready" -h localhost -p 5432 && "$PGBIN/pg_isready" -h localhost -p 5433 && break
  sleep 1
done

# create users/databases
create_sql() {
  local port="$1" db="$2"
  gosu postgres "$PGBIN/psql" -p "$port" -v ON_ERROR_STOP=1 -v db="$db" <<'SQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'conduktor') THEN
    CREATE ROLE conduktor LOGIN PASSWORD 'change_me';
  END IF;
END$$;
CREATE DATABASE :"db" OWNER conduktor;
ALTER DATABASE :"db" OWNER TO conduktor;
SQL
}

create_sql 5432 "conduktor-console" || true
create_sql 5433 "conduktor-sql" || true

# allow more async IO for redpanda
echo 1048576 > /proc/sys/fs/aio-max-nr || true

# Redpanda using packaged config (server binary directly)
/usr/bin/redpanda \
  --redpanda-cfg /etc/redpanda/redpanda.yaml \
  --smp 1 \
  --memory 1G \
  --overprovisioned \
  --default-log-level=info >"$LOGDIR/redpanda.log" 2>&1 &

# Gateway
sed -i 's#/app/#/opt/gateway-app/#g' /opt/gateway-app/jib-classpath-file || true
env \
  JAVA_HOME=/opt/java/openjdk \
  PATH=/opt/java/openjdk/bin:$PATH \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  GATEWAY_ACL_ENABLED=false \
  JAVA_OPTS="-Xms256m -Xmx512m" \
  sh -c 'cd /opt/gateway-app && exec "$JAVA_HOME/bin/java" $JAVA_OPTS -cp @/opt/gateway-app/jib-classpath-file @/opt/gateway-app/jib-main-class-file' >"$LOGDIR/gateway.log" 2>&1 &

# Monitoring (Cortex) - start with default config if present
# Monitoring disabled for now (config missing)

# Console env
env \
  CDK_DATABASE_URL=postgresql://conduktor:change_me@localhost:5432/conduktor-console \
  CDK_KAFKASQL_DATABASE_URL=postgresql://conduktor:change_me@localhost:5433/conduktor-sql \
  CDK_ORGANIZATION_NAME=getting-started \
  CDK_ADMIN_EMAIL=admin@demo.dev \
  CDK_ADMIN_PASSWORD=123=ABC_abc  \
  CDK_CLUSTERS_0_ID=local-kafka \
  CDK_CLUSTERS_0_NAME=local-kafka \
  CDK_CLUSTERS_0_BOOTSTRAPSERVERS=localhost:9092 \
  CDK_CLUSTERS_0_SCHEMAREGISTRY_URL=http://localhost:8081 \
  CDK_CLUSTERS_0_COLOR=#6A57C8 \
  CDK_CLUSTERS_0_ICON=kafka \
  CDK_CLUSTERS_1_ID=cdk-gateway \
  CDK_CLUSTERS_1_NAME=cdk-gateway \
  CDK_CLUSTERS_1_BOOTSTRAPSERVERS=localhost:6969 \
  CDK_CLUSTERS_1_SCHEMAREGISTRY_URL=http://localhost:8081 \
  CDK_CLUSTERS_1_KAFKAFLAVOR_URL=http://localhost:8888 \
  CDK_CLUSTERS_1_KAFKAFLAVOR_USER=admin \
  CDK_CLUSTERS_1_KAFKAFLAVOR_PASSWORD=conduktor \
  CDK_CLUSTERS_1_KAFKAFLAVOR_VIRTUALCLUSTER=passthrough \
  CDK_CLUSTERS_1_KAFKAFLAVOR_TYPE=Gateway \
  CDK_CLUSTERS_1_COLOR=#6A57C8 \
  CDK_CLUSTERS_1_ICON=dog \
  CDK_MONITORING_CORTEX-URL=http://localhost:9009/ \
  CDK_MONITORING_ALERT-MANAGER-URL=http://localhost:9010/ \
  CDK_MONITORING_CALLBACK-URL=http://localhost:8080/monitoring/api/ \
  CDK_MONITORING_NOTIFICATIONS-CALLBACK-URL=http://localhost:8080 \
  BE_CONFIG_ENV=production \
  sh -c 'cd /opt/conduktor/scripts && exec ./run.sh' >"$LOGDIR/console.log" 2>&1 &

# Data generator
env \
  JAVA_HOME=/opt/java/openjdk \
  PATH=/opt/java/openjdk/bin:$PATH \
  KAFKA_SCHEMA_REGISTRY_URL=http://localhost:8081 \
  GATEWAY_ADMIN_API=http://localhost:8888 \
  KAFKA_BOOTSTRAP_SERVERS=localhost:6969 \
  sh -c 'cd /opt/datagen-app && exec "$JAVA_HOME/bin/java" -jar /opt/datagen-app/myapp.jar' >"$LOGDIR/datagen.log" 2>&1 &

echo "All services started. Tailing logs..."

exec tail -F "$LOGDIR"/*.log
