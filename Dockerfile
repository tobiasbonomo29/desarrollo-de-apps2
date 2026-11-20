# =============================================================================
#  MediConecta - imagen de la aplicacion (WildFly 36 + WAR)
# =============================================================================
#
#  Dos etapas:
#    1. "compilacion": Maven compila el WAR (con el linter) y baja el driver de
#       PostgreSQL. Las herramientas de build no llegan a la imagen final.
#    2. imagen final: la oficial de WildFly, configurada con el CLI en modo
#       embebido (driver, datasource, seguridad JWT, reintentos/DLQ de JMS y
#       timeouts) y con el WAR ya desplegado.
#
#  Los tests no corren aca: corren en el pipeline de CI antes de construir la
#  imagen. Para levantar todo junto (base + migraciones + app): compose.yaml.
# =============================================================================

FROM maven:3.9.9-eclipse-temurin-17 AS compilacion
WORKDIR /fuente

# Primero solo el pom: si no cambia, Docker reutiliza la capa con las
# dependencias ya descargadas.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline \
    && mvn -B -q dependency:copy -Dartifact=org.postgresql:postgresql:42.7.4 -DoutputDirectory=/drivers

COPY config ./config
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -Djacoco.skip=true


FROM quay.io/wildfly/wildfly:36.0.1.Final-jdk17

ENV JBOSS_HOME=/opt/jboss/wildfly

COPY --from=compilacion --chown=jboss:jboss /drivers/postgresql-42.7.4.jar /tmp/postgresql.jar
COPY --chown=jboss:jboss docker/configurar-wildfly-docker.cli scripts/wildfly/configurar-mensajeria.cli \
     scripts/wildfly/configurar-limites.cli scripts/wildfly/configurar-endurecimiento.cli /tmp/cli/

# Un solo script: el bloque de Docker (abre el servidor embebido), los scripts
# compartidos con la instalacion local y el cierre del servidor embebido.
RUN $JBOSS_HOME/bin/jboss-cli.sh --command="module add --name=org.postgresql --resources=/tmp/postgresql.jar --dependencies=jakarta.transaction.api,java.sql,java.logging" \
    && cat /tmp/cli/configurar-wildfly-docker.cli /tmp/cli/configurar-mensajeria.cli /tmp/cli/configurar-limites.cli \
           /tmp/cli/configurar-endurecimiento.cli > /tmp/cli/todo.cli \
    && echo "stop-embedded-server" >> /tmp/cli/todo.cli \
    && $JBOSS_HOME/bin/jboss-cli.sh --file=/tmp/cli/todo.cli \
    && rm -rf /tmp/cli /tmp/postgresql.jar \
       $JBOSS_HOME/standalone/configuration/standalone_xml_history \
       $JBOSS_HOME/standalone/data $JBOSS_HOME/standalone/log $JBOSS_HOME/standalone/tmp

COPY --from=compilacion --chown=jboss:jboss /fuente/target/mediconecta.war $JBOSS_HOME/standalone/deployments/

EXPOSE 8080

# Docker considera "healthy" al contenedor cuando la aplicacion responde y
# llega a la base y al broker JMS (GET /api/salud devuelve 200).
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/mediconecta/api/salud > /dev/null || exit 1

# standalone-full.xml es obligatorio: es el perfil que trae JMS (Artemis).
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "0.0.0.0"]
