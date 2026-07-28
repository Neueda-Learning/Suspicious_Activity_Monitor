#!/usr/bin/env bash
# Starts Postgres and the application with a known-good JDK.
#
# Lombok cannot run under JDK 23 (the default on our machines), which makes `mvn compile`
# fail with a wall of "cannot find symbol" errors. Pin the JDK here so a cold terminal works.
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export JAVA_HOME
echo "JDK: $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

docker compose up -d
until docker exec aml-postgres pg_isready -U aml -d aml >/dev/null 2>&1; do
  echo "waiting for postgres..."; sleep 2
done

exec mvn spring-boot:run
