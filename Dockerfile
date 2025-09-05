# Stage 1: Build the app
FROM openjdk:19-jdk AS build
WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
COPY src src

# Make Maven wrapper executable & build the jar
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the app
FROM openjdk:19-jdk
VOLUME /tmp
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
EXPOSE 8080
