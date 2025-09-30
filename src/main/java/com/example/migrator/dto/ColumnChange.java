package com.example.migrator.dto;

public class ColumnChange {
    private String table;
    private String column;
    private String newColumn;
    private String newType;
    private boolean primaryKey;

    public ColumnChange() {}

    public ColumnChange(String table, String column, String newColumn, String newType, boolean primaryKey) {
        this.table = table;
        this.column = column;
        this.newColumn = newColumn;
        this.newType = newType;
        this.primaryKey = primaryKey;
    }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }
    public String getNewColumn() { return newColumn; }
    public void setNewColumn(String newColumn) { this.newColumn = newColumn; }
    public String getNewType() { return newType; }
    public void setNewType(String newType) { this.newType = newType; }
    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
}