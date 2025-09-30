package com.example.migrator.service;

import jakarta.persistence.Entity;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.ClasspathHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

@Service
public class EntityScannerService {

    public Map<String, Object> scanAll() throws Exception {
        // 1. 외부 디렉토리 설정 (JVM 옵션 -Dscan.source.root 로 넘겨줌)
        String sourceRoot = System.getProperty("scan.source.root");
        if (sourceRoot == null) {
            throw new IllegalStateException("JVM 옵션 -Dscan.source.root 가 필요합니다.");
        }

        // 2. 클래스패스 URL 수집
        List<URL> urls = new ArrayList<>();
        urls.addAll(ClasspathHelper.forJavaClassPath()); // 기본 JAR 내부

        File rootDir = new File(sourceRoot);
        if (rootDir.exists()) {
            // /build/classes/java/main 같은 하위 디렉토리를 전부 classpath에 올림
            rootDir.walk()
                    .filter(f -> f.getName().equals("classes"))
                    .forEach(f -> {
                        try {
                            urls.add(f.toURI().toURL());
                        } catch (Exception ignored) {}
                    });
        }

        // 3. Reflections 초기화
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(urls)
                .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes));

        Set<Class<?>> ents = new HashSet<>();
        ents.addAll(reflections.getTypesAnnotatedWith(Entity.class));
        // javax 지원도 추가
        try { ents.addAll(reflections.getTypesAnnotatedWith(javax.persistence.Entity.class)); } catch (Throwable ignore) {}

        // 4. 결과 JSON-like 구조 만들기
        Map<String,Object> out = new HashMap<>();
        List<Map<String,Object>> entities = new ArrayList<>();

        for (Class<?> cls : ents) {
            Map<String,Object> em = new HashMap<>();
            em.put("className", cls.getSimpleName());
            em.put("qualifiedName", cls.getName());

            jakarta.persistence.Table t = cls.getAnnotation(jakarta.persistence.Table.class);
            if (t == null) {
                javax.persistence.Table t2 = cls.getAnnotation(javax.persistence.Table.class);
                em.put("tableName", (t2 != null && !t2.name().isEmpty()) ? t2.name() : cls.getSimpleName().toLowerCase());
            } else {
                em.put("tableName", !t.name().isEmpty() ? t.name() : cls.getSimpleName().toLowerCase());
            }

            List<Map<String,Object>> fields = new ArrayList<>();
            for (var f : cls.getDeclaredFields()) {
                Map<String,Object> fm = new HashMap<>();
                fm.put("fieldName", f.getName());
                fm.put("type", f.getType().getSimpleName());

                jakarta.persistence.Column c = f.getAnnotation(jakarta.persistence.Column.class);
                javax.persistence.Column c2 = f.getAnnotation(javax.persistence.Column.class);
                String colName = (c != null && !c.name().isEmpty()) ? c.name()
                        : (c2 != null && !c2.name().isEmpty()) ? c2.name()
                        : f.getName();
                fm.put("columnName", colName);

                fm.put("primaryKey", f.isAnnotationPresent(jakarta.persistence.Id.class)
                        || f.isAnnotationPresent(javax.persistence.Id.class));
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
