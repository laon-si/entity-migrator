
package com.example.migrator.service;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;
@Service
public class EntityScannerService {
    private final String basePackage = "com.example";

    public Map<String, Object> scanAll() {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(basePackage))
                .forPackages(basePackage)
                .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes));

        Set<Class<?>> ents = reflections.getTypesAnnotatedWith(Entity.class);
        Map<String,Object> out = new HashMap<>();
        List<Map<String,Object>> entities = new ArrayList<>();
        for (Class<?> cls : ents) {
            Map<String,Object> em = new HashMap<>();
            em.put("className", cls.getSimpleName());
            em.put("qualifiedName", cls.getName());
            jakarta.persistence.Table t = cls.getAnnotation(jakarta.persistence.Table.class);
            em.put("tableName", t != null && !t.name().isEmpty() ? t.name() : cls.getSimpleName().toLowerCase());

            List<Map<String,Object>> fields = new ArrayList<>();
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                Map<String,Object> fm = new HashMap<>();
                fm.put("fieldName", f.getName());
                fm.put("type", f.getType().getSimpleName());
                jakarta.persistence.Column c = f.getAnnotation(jakarta.persistence.Column.class);
                fm.put("columnName", c != null && !c.name().isEmpty() ? c.name() : f.getName());
                fm.put("primaryKey", f.getAnnotation(jakarta.persistence.Id.class) != null);
                fields.add(fm);
            }
            em.put("fields", fields);
            entities.add(em);
        }
        out.put("package", basePackage);
        out.put("entities", entities);
        out.put("scannedAt", new Date().toString());
        return out;
    }
}