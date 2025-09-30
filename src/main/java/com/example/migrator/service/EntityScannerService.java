package com.example.migrator.service;

import jakarta.persistence.Entity;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

@Service
public class EntityScannerService {

    public Map<String, Object> scanAll() throws IOException {
        String sourceRoot = System.getProperty("scan.source.root");
        if (sourceRoot == null) {
            throw new IllegalStateException("JVM 옵션 -Dscan.source.root 가 필요합니다.");
        }

        // 1. URL 모으기
        List<URL> urls = new ArrayList<>();
        try (var paths = Files.walk(Paths.get(sourceRoot))) {
            paths.filter(Files::isDirectory)
                    .filter(p -> p.toString().endsWith("classes/java/main"))
                    .forEach(p -> {
                        try {
                            urls.add(p.toUri().toURL());
                        } catch (Exception ignored) {}
                    });
        }

        // 2. Reflections 구성
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(urls)
                .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes));

        Set<Class<?>> ents = new HashSet<>();
        try { ents.addAll(reflections.getTypesAnnotatedWith(Entity.class)); } catch (Throwable ignore) {}
        try { ents.addAll(reflections.getTypesAnnotatedWith(javax.persistence.Entity.class)); } catch (Throwable ignore) {}

        // 3. JSON 형태로 결과
        Map<String,Object> out = new HashMap<>();
        List<Map<String,Object>> entities = new ArrayList<>();

        for (Class<?> cls : ents) {
            Map<String,Object> em = new HashMap<>();
            em.put("className", cls.getSimpleName());
            em.put("qualifiedName", cls.getName());

            // 테이블명
            String tableName = cls.getSimpleName().toLowerCase();
            var tJakarta = cls.getAnnotation(jakarta.persistence.Table.class);
            var tJavax = cls.getAnnotation(javax.persistence.Table.class);
            if (tJakarta != null && !tJakarta.name().isEmpty()) {
                tableName = tJakarta.name();
            } else if (tJavax != null && !tJavax.name().isEmpty()) {
                tableName = tJavax.name();
            }
            em.put("tableName", tableName);

            // 필드들
            List<Map<String,Object>> fields = new ArrayList<>();
            for (var f : cls.getDeclaredFields()) {
                Map<String,Object> fm = new HashMap<>();
                fm.put("fieldName", f.getName());
                fm.put("type", f.getType().getSimpleName());

                String columnName = f.getName();
                var cJakarta = f.getAnnotation(jakarta.persistence.Column.class);
                var cJavax = f.getAnnotation(javax.persistence.Column.class);
                if (cJakarta != null && !cJakarta.name().isEmpty()) {
                    columnName = cJakarta.name();
                } else if (cJavax != null && !cJavax.name().isEmpty()) {
                    columnName = cJavax.name();
                }
                fm.put("columnName", columnName);

                boolean isPk = f.isAnnotationPresent(jakarta.persistence.Id.class)
                        || f.isAnnotationPresent(javax.persistence.Id.class);
                fm.put("primaryKey", isPk);

                fields.add(fm);
            }
            em.put("fields", fields);
            entities.add(em);
        }

        out.put("count", entities.size());
        out.put("entities", entities);
        out.put("scannedAt", new Date().toString());
        return out;
    }
}
