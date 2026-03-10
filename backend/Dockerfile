# multi-stage build for the Spring Boot backend

# ---------- build stage ----------
FROM maven:3.9.4-eclipse-temurin-17 as build
WORKDIR /workspace

# copy maven configuration first to take advantage of layer caching
COPY pom.xml ./
COPY src ./src

# build the application (skip tests to make the image faster)
RUN mvn -B clean package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# copy the fat jar produced by the build stage (name may vary depending on version)
COPY --from=build /workspace/target/*.jar app.jar

# expose the port that the Spring Boot app listens on
# some platforms (Render, Heroku, etc.) look for a PORT env var
ENV PORT=8080
EXPOSE ${PORT}

# run the jar
ENTRYPOINT ["sh","-c","java -jar /app/app.jar --server.port=$PORT"]
