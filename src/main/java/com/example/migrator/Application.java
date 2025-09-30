
package com.example.migrator;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    CommandLineRunner loadSchema(DataSource ds) {
        return args -> {
            try (Connection c = ds.getConnection()) {
                ScriptUtils.executeSqlScript(c, new ClassPathResource("schema-h2.sql"));
            } catch (Exception e) {
                // ignore
            }
        };
    }
}
