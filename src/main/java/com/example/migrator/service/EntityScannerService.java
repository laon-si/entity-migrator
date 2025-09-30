package com.example.migrator.service;

import jakarta.persistence.*;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EntityScannerService {

    private static List<String> resolveBasePackages() {
        String list = System.getProperty("scan.base.packages");
        if (list != null && !list.isBlank()) {
            return Arrays.stream(list.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .distinct().collect(Collectors.toList());
        }
        String prefix = System.getProperty("scan.base.package.prefix");
        if (prefix != null && !prefix.isBlank()) {
            return List.of(prefix);
        }
        return List.of("headquarter"); // 기본값
    }

    private static Set<URL> urlsForPackages(List<String> pkgs) {
        Set<URL> urls = new HashSet<>();
        for (String p : pkgs) {
            urls.addAll(ClasspathHelper.forPackage(p));
        }
        return urls;
    }

    public Map<String, Object> scanAll() {
        List<String> bases = resolveBasePackages();

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(urlsForPackages(bases))
                .forPackages(bases.toArray(String[]::new))
                .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes));

        // @Entity / @Embeddable / @MappedSuperclass 전부 스캔
        Set<Class<?>> entities = new HashSet<>();
        entities.addAll(reflections.getTypesAnnotatedWith(Entity.class));
        entities.addAll(reflections.getTypesAnnotatedWith(Embeddable.class));
        entities.addAll(reflections.getTypesAnnotatedWith(MappedSuperclass.class));

        List<Map<String, Object>> entityDtos = new ArrayList<>();
        for (Class<?> cls : entities) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("qualifiedName", cls.getName());
            em.put("className", cls.getSimpleName());

            Table t = cls.getAnnotation(Table.class);
            String table = (t != null && t.name() != null && !t.name().isBlank())
                    ? t.name()
                    : cls.getSimpleName().toLowerCase(Locale.ROOT);
            em.put("tableName", table);

            List<Map<String, Object>> fields = extractAllFields(cls);
            em.put("fields", fields);

            em.put("package", cls.getPackageName());
            em.put("module", guessModule(cls.getPackageName()));

            entityDtos.add(em);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("basePackages", bases);
        out.put("entities", entityDtos);
        out.put("count", entityDtos.size());
        out.put("scannedAt", new Date().toString());
        return out;
    }

    // 부모 클래스까지 포함한 필드 추출
    private static List<Map<String, Object>> extractAllFields(Class<?> cls) {
        List<Map<String, Object>> fields = new ArrayList<>();
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("fieldName", f.getName());
                fm.put("type", f.getType().getSimpleName());

                Column col = f.getAnnotation(Column.class);
                String colName = (col != null && col.name() != null && !col.name().isBlank())
                        ? col.name()
                        : f.getName();
                fm.put("columnName", colName);

                boolean pk = f.getAnnotation(Id.class) != null;
                fm.put("primaryKey", pk);

                fields.add(fm);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static String guessModule(String pkg) {
        if (pkg == null) return "";
        String[] parts = pkg.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            if ("headquarter".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }
}