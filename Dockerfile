# Imagen del backend para Render.
# Etapa 1: compilar con el JDK. Etapa 2: ejecutar solo el .jar con el JRE.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Primero solo lo necesario para descargar dependencias (se cachea entre despliegues)
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# skill backend-seguro §6: la app no corre como root
RUN useradd --system --uid 10001 --no-create-home app
COPY --from=build /app/target/todos-0.0.1-SNAPSHOT.jar app.jar
USER app

# Render asigna el puerto con la variable PORT (server.port=${PORT:8080})
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
