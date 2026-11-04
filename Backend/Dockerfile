# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY Backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY Backend/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS java-runtime

FROM python:3.12-slim-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl libstdc++6 libgomp1 zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system mundus \
    && useradd --system --create-home --gid mundus mundus

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk

WORKDIR /app
COPY Preparador/requirements-vector.txt /app/preparador/requirements-vector.txt
RUN pip install --no-cache-dir torch==2.8.0 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r /app/preparador/requirements-vector.txt
COPY Preparador/json_to_vector.py /app/preparador/json_to_vector.py
COPY --from=build --chown=mundus:mundus /workspace/target/backend.jar app.jar
RUN mkdir -p /app/datos/guias /app/modelos/huggingface \
    && chown -R mundus:mundus /app/datos /app/modelos

USER mundus
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=30 -Djava.awt.headless=true"
ENV VECTOR_PYTHON=python
ENV VECTOR_SCRIPT=/app/preparador/json_to_vector.py
ENV GUIAS_DIRECTORIO=/app/datos/guias
ENV VECTOR_DIRECTORIO=/app/datos/guias/vectores
ENV VECTOR_CACHE=/app/modelos/huggingface
ENV HF_HUB_OFFLINE=1
ENV TRANSFORMERS_OFFLINE=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONIOENCODING=utf-8
ENV OMP_THREAD_LIMIT=1
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
