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

    private static final String PROP_SOURCE_ROOT = "scan.source.root";

    /**
     * 엔티티 파일 수정
     * @param entityMap {"qualifiedName", "newTableName", "fields":[{newColumnName, newType, primaryKey}]}
     */
    public void updateEntity(Map<String, Object> entityMap) throws IOException {
        String root = System.getProperty(PROP_SOURCE_ROOT);
        if (root == null || root.isBlank()) {
            throw new IllegalStateException("필수 JVM 옵션 -D" + PROP_SOURCE_ROOT + " 가 없습니다.");
        }

        // 대상 클래스 경로 계산
        String qualifiedName = (String) entityMap.get("qualifiedName");
        String relativePath = qualifiedName.replace(".", "/") + ".java";
        Path filePath = Paths.get(root).resolve(relativePath);

        if (!Files.exists(filePath)) {
            throw new IllegalStateException("대상 엔티티 파일 없음: " + filePath);
        }

        // 파일 읽기
        List<String> lines = Files.readAllLines(filePath);

        // 테이블명 변경
        String newTable = (String) entityMap.get("newTableName");
        if (newTable != null && !newTable.isBlank()) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("@Table")) {
                    lines.set(i, lines.get(i).replaceFirst("name\\s*=\\s*\".*?\"", "name=\"" + newTable + "\""));
                }
            }
        }

        // 컬럼명, 타입, PK 변경
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) entityMap.get("fields");
        if (fields != null) {
            for (Map<String, Object> f : fields) {
                String fieldName = (String) f.get("fieldName");
                String newColumn = (String) f.get("newColumnName");
                String newType = (String) f.get("newType");
                boolean pk = Boolean.TRUE.equals(f.get("primaryKey"));

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);

                    // 필드 선언 줄 찾기
                    if (line.contains(fieldName)) {
                        // 타입 변경
                        if (newType != null && !newType.isBlank()) {
                            lines.set(i, line.replaceFirst("\\b\\w+\\b\\s+" + fieldName, newType + " " + fieldName));
                        }
                    }

                    // 컬럼 어노테이션 변경
                    if (line.contains("@Column") && newColumn != null && !newColumn.isBlank()) {
                        lines.set(i, line.replaceFirst("name\\s*=\\s*\".*?\"", "name=\"" + newColumn + "\""));
                    }

                    // PK 어노테이션 추가/삭제
                    if (pk && !line.contains("@Id") && line.contains(fieldName)) {
                        lines.add(i, "    @Id");
                        i++;
                    }
                }
            }
        }

        // 저장 (덮어쓰기)
        Files.write(filePath, lines, StandardOpenOption.TRUNCATE_EXISTING);
    }

}
