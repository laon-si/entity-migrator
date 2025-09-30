
Entity Schema Migrator - GUI edition
-----------------------------------

- Java 21, Gradle, Spring Boot
- Default DB: H2 (file mode), PostgreSQL profile available.
- Start server and open http://localhost:8080/ui to test GUI.
- The app scans entities in package: com.example.migrator.entity
- When you press Save on the UI, the system will:
  1) Generate per-table DDLs and execute them in a per-table JDBC transaction.
  2) If a table's DDLs all succeed, update the Java entity source files under src/main/java using JavaParser.
  3) If any DDL in a table fails, that table's changes are rolled back and source files are not modified.

Notes:
- Gradle wrapper jar is placeholder. If wrapper doesn't work, install Gradle 8.7+ and set IntelliJ to use local Gradle.
- This package contains sample entities to test with.
