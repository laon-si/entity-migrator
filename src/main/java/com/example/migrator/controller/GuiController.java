
package com.example.migrator.controller;

import com.example.migrator.dto.ColumnChange;
import com.example.migrator.service.EntityScannerService;
import com.example.migrator.service.SchemaMigrationService;
import com.example.migrator.service.SourceModifierService;
import com.example.migrator.service.SourceModifierService.FieldChange;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.*;

@Controller
public class GuiController {

    private final EntityScannerService scanner;
    private final SchemaMigrationService schemaService;
    private final SourceModifierService sourceService;

    public GuiController(EntityScannerService scanner, SchemaMigrationService schemaService, SourceModifierService sourceService) {
        this.scanner = scanner;
        this.schemaService = schemaService;
        this.sourceService = sourceService;
    }

    @GetMapping("/ui")
    public String ui() {
        return "ui";
    }

    @GetMapping("/api/scan/entities")
    @ResponseBody
    public Map<String, Object> scanEntities() {
        return scanner.scanAll();
    }

    @PostMapping("/api/apply")
    @ResponseBody
    public Map<String, Object> apply(@RequestBody Map<String, Object> body) {
        List<Map<String,Object>> changes = (List<Map<String,Object>>) body.get("changes");
        List<ColumnChange> dtoList = new ArrayList<>();
        Map<String, List<Map<String,Object>>> byTable = new HashMap<>();
        for (Map<String,Object> m : changes) {
            String table = (String) m.get("table"); 
            String column = (String) m.get("column"); 
            String newColumn = (String) m.get("newColumn"); 
            String newType = (String) m.get("newType"); 
            Boolean pk = m.get("primaryKey") != null ? (Boolean) m.get("primaryKey") : false;
            String qualified = (String) m.get("qualifiedName"); 
            String fieldName = (String) m.get("fieldName"); 
            ColumnChange cc = new ColumnChange(table, column, newColumn, newType, pk);
            dtoList.add(cc);
            byTable.computeIfAbsent(table, k-> new ArrayList<>()).add(m);
        }

        Map<String, String> sqlResult = schemaService.applyChanges(dtoList);
        Map<String, Object> out = new HashMap<>();
        out.put("sqlResult", sqlResult);

        Map<String, Object> applyResult = new HashMap<>();
        for (String table : byTable.keySet()) {
            String status = sqlResult.getOrDefault(table, "FAILED");
            if (!"OK".equals(status)) {
                applyResult.put(table, Map.of("status", status, "message", "SQL failed, source not modified"));
                continue;
            }
            List<Map<String,Object>> tableChanges = byTable.get(table);
            if (tableChanges == null) continue;
            String qualifiedName = null;
            String newTableName = null;
            List<FieldChange> fcs = new ArrayList<>();
            for (Map<String,Object> m : tableChanges) {
                if (qualifiedName == null && m.get("qualifiedName") != null) qualifiedName = (String) m.get("qualifiedName");
                if (newTableName == null && m.get("newTableName") != null) newTableName = (String) m.get("newTableName");
                String fieldName = (String) m.get("fieldName");
                String newColumn = (String) m.get("newColumn");
                String newType = (String) m.get("newType");
                if (fieldName != null && newColumn != null) {
                    fcs.add(new FieldChange(fieldName, newColumn, newType));
                }
            }
            if (qualifiedName == null) {
                applyResult.put(table, Map.of("status", "NO_SOURCE_INFO", "message", "qualifiedName missing - cannot modify source"));
                continue;
            }
            try {
                sourceService.applyEntityChanges(qualifiedName, fcs, newTableName);
                applyResult.put(table, Map.of("status", "SOURCE_UPDATED"));
            } catch (Exception ex) {
                applyResult.put(table, Map.of("status", "SOURCE_FAILED", "message", ex.getMessage()));
            }
        }

        out.put("sourceResult", applyResult);
        return out;
    }
