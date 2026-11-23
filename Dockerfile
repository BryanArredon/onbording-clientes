# syntax=docker/dockerfile:1

# ==============================================================================
# Etapa 1: Build con Maven y Eclipse Temurin 21
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 1. Copiar descriptor del proyecto para cachear dependencias
COPY pom.xml .

# 2. Descargar dependencias en modo offline
RUN mvn dependency:go-offline -B || true

# 3. Copiar código fuente y compilar artefacto
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Etapa 2: Runtime ligero, seguro y sin privilegios de root
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Instalar tzdata para zona horaria de México y crear usuario sin privilegios
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/America/Mexico_City /etc/localtime \
    && echo "America/Mexico_City" > /etc/timezone \
    && addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar el jar compilado desde el builder
COPY --from=builder /build/target/onboarding-clientes.jar app.jar

# Cambiar permisos al usuario no root
RUN chown -R appuser:appgroup /app
USER appuser

# Exponer el puerto del microservicio (por defecto 8082)
EXPOSE 8082

# Variables de entorno por defecto
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ="America/Mexico_City" \
    JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=45.0 -XX:InitialRAMPercentage=20.0 -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=America/Mexico_City"

# Healthcheck usando Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=50s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider "http://localhost:${PORT:-8082}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
