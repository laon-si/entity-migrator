package com.example.migrator.service;

import com.example.migrator.dto.ColumnChange;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchemaMigrationService {
    private final DataSource dataSource;

    public SchemaMigrationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, String> applyChanges(List<ColumnChange> changes) {
        Map<String, List<ColumnChange>> byTable = changes.stream()
                .collect(Collectors.groupingBy(ColumnChange::getTable));

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<ColumnChange>> e : byTable.entrySet()) {
            String table = e.getKey();
            List<ColumnChange> ops = e.getValue();
            try (Connection conn = dataSource.getConnection()) {
                boolean originalAuto = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    DatabaseMetaData md = conn.getMetaData();
                    String dbName = md.getDatabaseProductName().toLowerCase();
                    List<String> stmts = new ArrayList<>();
                    for (ColumnChange c : ops) {
                        stmts.addAll(buildSqlForChange(dbName, table, c));
                    }
                    try (Statement st = conn.createStatement()) {
                        for (String sql : stmts) {
                            st.execute(sql);
                        }
                    }
                    conn.commit();
                    result.put(table, "OK");
                } catch (Exception inner) {
                    try { conn.rollback(); } catch (SQLException ex) {}
                    result.put(table, "FAILED: " + inner.getMessage());
                } finally {
                    try { conn.setAutoCommit(originalAuto); } catch (SQLException ignore) {}
                }
            } catch (SQLException ex) {
                result.put(table, "FAILED-CONNECTION: " + ex.getMessage());
            }
        }
        return result;
    }

    private List<String> buildSqlForChange(String dbName, String table, ColumnChange c) {
        List<String> out = new ArrayList<>();
        boolean isPostgres = dbName.contains("postgres");
        boolean isH2 = dbName.contains("h2");
        String quotedTable = quote(table);

        if (c.getNewColumn() != null && !c.getNewColumn().isEmpty() && !c.getNewColumn().equals(c.getColumn())) {
            if (isPostgres) {
                out.add(String.format("ALTER TABLE %s RENAME COLUMN %s TO %s", quotedTable, quote(c.getColumn()), quote(c.getNewColumn())));
                if (c.getNewType() != null && !c.getNewType().isEmpty()) {
                    out.add(String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s USING %s::%s", quotedTable, quote(c.getNewColumn()), c.getNewType(), quote(c.getNewColumn()), c.getNewType()));
                }
                return out;
            } else if (isH2) {
                out.add(String.format("ALTER TABLE %s ALTER COLUMN %s RENAME TO %s", quotedTable, quote(c.getColumn()), quote(c.getNewColumn())));
                if (c.getNewType() != null && !c.getNewType().isEmpty()) {
                    out.add(String.format("ALTER TABLE %s ALTER COLUMN %s SET DATA TYPE %s", quotedTable, quote(c.getNewColumn()), c.getNewType()));
                }
                return out;
            }
        }

        if (c.getNewType() != null && !c.getNewType().isEmpty() && (c.getNewColumn() == null || c.getNewColumn().isEmpty())) {
            if (isPostgres) {
                out.add(String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s USING %s::%s", quotedTable, quote(c.getColumn()), c.getNewType(), quote(c.getColumn()), c.getNewType()));
                return out;
            } else if (isH2) {
                out.add(String.format("ALTER TABLE %s ALTER COLUMN %s SET DATA TYPE %s", quotedTable, quote(c.getColumn()), c.getNewType()));
                return out;
            }
        }

        if ((c.getColumn() == null || c.getColumn().isEmpty()) && c.getNewColumn() != null && !c.getNewColumn().isEmpty()) {
            String typ = c.getNewType() != null && !c.getNewType().isEmpty() ? c.getNewType() : (isPostgres ? "TEXT" : "VARCHAR(255)");
            out.add(String.format("ALTER TABLE %s ADD COLUMN %s %s", quotedTable, quote(c.getNewColumn()), typ));
            return out;
        }

        throw new IllegalArgumentException("Unsupported change type for: " + c.getTable() + " / " + c.getColumn());
    }

    private String quote(String ident) {
        return """ + ident + """;
    }
}