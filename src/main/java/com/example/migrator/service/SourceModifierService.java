
package com.example.migrator.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
public class SourceModifierService {

    // assumes source root in the running project
    private final String SOURCE_ROOT = System.getProperty("user.dir") + "/src/main/java";

    public void applyEntityChanges(String qualifiedClassName, List<FieldChange> fieldChanges, String newTableName) throws Exception {
        // find file path from qualified class name
        String path = SOURCE_ROOT + "/" + qualifiedClassName.replace('.', '/') + ".java";
        File f = new File(path);
        if (!f.exists()) throw new IllegalArgumentException("Source file not found: " + path);
        FileInputStream in = new FileInputStream(f);
        CompilationUnit cu = StaticJavaParser.parse(in, StandardCharsets.UTF_8);
        in.close();

        Optional<ClassOrInterfaceDeclaration> clsOpt = cu.getClassByName(qualifiedClassName.substring(qualifiedClassName.lastIndexOf('.')+1));
        if (clsOpt.isEmpty()) throw new IllegalArgumentException("Class not found in source: " + qualifiedClassName);
        ClassOrInterfaceDeclaration cls = clsOpt.get();

        // update @Table name if needed
        if (newTableName != null && !newTableName.isEmpty()) {
            cls.getAnnotationByName("Table").ifPresentOrElse(a -> {
                if (a.isNormalAnnotationExpr()) {
                    NormalAnnotationExpr nae = a.asNormalAnnotationExpr();
                    Optional<MemberValuePair> pair = nae.getPairs().stream().filter(p -> p.getNameAsString().equals("name")).findFirst();
                    if (pair.isPresent()) {
                        pair.get().setValue(new StringLiteralExpr(newTableName));
                    } else {
                        nae.addPair("name", new StringLiteralExpr(newTableName));
                    }
                }
            }, () -> {
                // add @Table(name="...")
                cls.addAnnotation("Table(name=\"" + newTableName + "\")");
            });
        }

        for (FieldChange fc : fieldChanges) {
            for (FieldDeclaration fd : cls.getFields()) {
                fd.getVariables().forEach(var -> {
                    if (var.getNameAsString().equals(fc.getFieldName())) {
                        fd.getAnnotationByName("Column").ifPresentOrElse(a -> {
                            if (a.isNormalAnnotationExpr()) {
                                NormalAnnotationExpr nae = a.asNormalAnnotationExpr();
                                Optional<MemberValuePair> pair = nae.getPairs().stream().filter(p -> p.getNameAsString().equals("name")).findFirst();
                                if (pair.isPresent()) {
                                    pair.get().setValue(new StringLiteralExpr(fc.getNewColumn()));
                                } else {
                                    nae.addPair("name", new StringLiteralExpr(fc.getNewColumn()));
                                }
                            } else {
                                a.replace(StaticJavaParser.parseAnnotation("@Column(name=\"" + fc.getNewColumn() + "\")"));
                            }
                        }, () -> {
                            fd.addAnnotation("Column(name=\"" + fc.getNewColumn() + "\")");
                        });
                    }
                });
            }
        }

        FileOutputStream out = new FileOutputStream(f);
        out.write(cu.toString().getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    public static class FieldChange {
        private String fieldName;
        private String newColumn;
        private String newType;

        public FieldChange() {}

        public FieldChange(String fieldName, String newColumn, String newType) {
            this.fieldName = fieldName;
            this.newColumn = newColumn;
            this.newType = newType;
        }

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getNewColumn() { return newColumn; }
        public void setNewColumn(String newColumn) { this.newColumn = newColumn; }
        public String getNewType() { return newType; }
        public void setNewType(String newType) { this.newType = newType; }
    }
