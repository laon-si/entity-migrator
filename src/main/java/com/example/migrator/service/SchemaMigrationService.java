
package com.example.migrator.service;

import com.example.migrator.dto.ColumnChange;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Service
public class SchemaMigrationService {
    private final DataSource dataSource;

    public SchemaMigrationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, String> applyChanges(List<ColumnChange> changes) {
        Map<String, List<ColumnChange>> byTable = new HashMap<>();
        for (ColumnChange c : changes) {
            byTable.computeIfAbsent(c.getTable(), k -> new ArrayList<>()).add(c);
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<ColumnChange>> e : byTable.entrySet()) {
            String table = e.getKey();
            List<ColumnChange> ops = e.getValue();
            try (Connection conn = dataSource.getConnection()) {
                boolean orig = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    DatabaseMetaData md = conn.getMetaData();
                    String db = md.getDatabaseProductName().toLowerCase();
                    try (Statement st = conn.createStatement()) {
                        for (ColumnChange c : ops) {
                            List<String> stmts = buildSql(db, table, c);
                            for (String s : stmts) {
                                st.execute(s);
                            }
                        }
                    }
                    conn.commit();
                    result.put(table, "OK");
                } catch (Exception ex) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    result.put(table, "FAILED: " + ex.getMessage());
                } finally {
                    conn.setAutoCommit(orig);
                }
            } catch (SQLException ex) {
                result.put(table, "FAILED-CONN: " + ex.getMessage());
            }
        }
        return result;
    }

    private List<String> buildSql(String db, String table, ColumnChange c) {
        boolean isPg = db.contains("postgres");
        boolean isH2 = db.contains("h2");
        List<String> out = new ArrayList<>();
        String tbl = quote(table);
        if (c.getNewColumn() != null && !c.getNewColumn().isEmpty() && !c.getNewColumn().equals(c.getColumn())) {
            if (isPg) {
                out.add(String.format("ALTER TABLE %s RENAME COLUMN %s TO %s", tbl, quote(c.getColumn()), quote(c.getNewColumn())));
                if (c.getNewType() != null && !c.getNewType().isEmpty()) {
                    out.add(String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s USING %s::%s", tbl, quote(c.getNewColumn()), c.getNewType(), quote(c.getNewColumn()), c.getNewType()));
                }
                return out;
            } else if (isH2) {
                out.add(String.format("ALTER TABLE %s ALTER COLUMN %s RENAME TO %s", tbl, quote(c.getColumn()), quote(c.getNewColumn())));
                if (c.getNewType() != null && !c.getNewType().isEmpty()) {
                    out.add(String.format("ALTER TABLE %s ALTER COLUMN %s SET DATA TYPE %s", tbl, quote(c.getNewColumn()), c.getNewType()));
                }
                return out;
            }
        }
        if (c.getNewType() != null && !c.getNewType().isEmpty() && (c.getNewColumn() == null || c.getNewColumn().isEmpty())) {
            if (isPg) {
                out.add(String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s USING %s::%s", tbl, quote(c.getColumn()), c.getNewType(), quote(c.getColumn()), c.getNewType()));
            } else if (isH2) {
                out.add(String.format("ALTER TABLE %s ALTER COLUMN %s SET DATA TYPE %s", tbl, quote(c.getColumn()), c.getNewType()));
            }
            return out;
        }
        if ((c.getColumn() == null || c.getColumn().isEmpty()) && c.getNewColumn() != null && !c.getNewColumn().isEmpty()) {
            String typ = c.getNewType() != null && !c.getNewType().isEmpty() ? c.getNewType() : (isPg ? "TEXT" : "VARCHAR(255)"); 
            out.add(String.format("ALTER TABLE %s ADD COLUMN %s %s", tbl, quote(c.getNewColumn()), typ));
            return out;
        }
        throw new IllegalArgumentException("Unsupported change: " + c.getTable() + "/" + c.getColumn());
    }

    private String quote(String id) {
        return "\"" + id + "\"";
    }
