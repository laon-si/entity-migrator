Entity Schema Migrator - Complete (Single-module)
================================================

This project is a runnable Spring Boot app (Java 21, Gradle) that demonstrates:
- Table-scoped DDL migrations for column rename/type/add.
- GUI + REST endpoints to select entities and apply changes.
- Default DB: H2 (for immediate local testing). PostgreSQL profile included.

How to run in IntelliJ:
1. Import the project (Open). In Gradle settings, prefer "Use local Gradle distribution" if gradlew issues.
2. Ensure JDK 21 is configured for the project.
3. If gradlew wrapper is OK on your machine, you can use it; otherwise install Gradle 8.7+ and point IntelliJ to it.
4. Run the Application main class: com.example.migrator.Application

Run via Gradle:
  ./gradlew bootRun

Build jar:
  ./gradlew bootJar
  java -jar build/libs/entity-schema-migrator-complete-0.0.1-SNAPSHOT.jar

PostgreSQL:
- Use `--spring.profiles.active=postgres` or set SPRING_PROFILES_ACTIVE=postgres
- A sample schema for Postgres is in src/main/resources/schema-postgres.sql