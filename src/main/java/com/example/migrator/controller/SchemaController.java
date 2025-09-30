package com.example.migrator.controller;

import com.example.migrator.dto.ColumnChange;
import com.example.migrator.service.SchemaMigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityManager;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
public class SchemaController {
    private final SchemaMigrationService migrationService;
    private final EntityManager em;

    public SchemaController(SchemaMigrationService migrationService, EntityManager em) {
        this.migrationService = migrationService;
        this.em = em;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/ui")
    public String ui() {
        return "ui";
    }

    @GetMapping("/api/entities")
    @ResponseBody
    public List<Map<String, Object>> listEntities() {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<EntityType<?>> entities = em.getMetamodel().getEntities();
        for (EntityType<?> et : entities) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", et.getName());
            m.put("className", et.getJavaType().getName());
            List<Map<String, String>> cols = new ArrayList<>();
            for (Attribute<?, ?> a : et.getAttributes()) {
                Map<String, String> col = new HashMap<>();
                col.put("name", a.getName());
                col.put("type", a.getJavaType().getSimpleName());
                cols.add(col);
            }
            m.put("columns", cols);
            out.add(m);
        }
        return out;
    }

    @PostMapping("/api/apply")
    @ResponseBody
    public ResponseEntity<Map<String, String>> applyJson(@RequestBody List<ColumnChange> changes) {
        return ResponseEntity.ok(migrationService.applyChanges(changes));
    }

    @PostMapping("/apply")
    public String applyManual(@RequestParam("table") String table,
                              @RequestParam("column") String column,
                              @RequestParam(value = "newColumn", required = false) String newColumn,
                              @RequestParam(value = "newType", required = false) String newType,
                              Model model) {
        ColumnChange c = new ColumnChange(table, column, newColumn, newType, false);
        Map<String, String> res = migrationService.applyChanges(List.of(c));
        model.addAttribute("result", res);
        return "result";
    }

    @PostMapping("/uploadCsv")
    public String uploadCsv(@RequestParam("file") MultipartFile file, Model model) throws Exception {
        List<ColumnChange> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                String table = parts.length>0? parts[0].trim(): "";
                String column = parts.length>1? parts[1].trim(): "";
                String newColumn = parts.length>2? parts[2].trim(): "";
                String newType = parts.length>3? parts[3].trim(): "";
                boolean pk = parts.length>4 && ("1".equals(parts[4].trim()) || "true".equalsIgnoreCase(parts[4].trim()));
                list.add(new ColumnChange(table, column, newColumn, newType, pk));
            }
        }
        Map<String, String> res = migrationService.applyChanges(list);
        model.addAttribute("result", res);
        return "result";
    }
}