package com.codelens.common.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * 变更文件信息
 */
public class ChangedFile {
    public String filePath;               // src/main/java/com/example/Service.java
    public String className;              // com.example.Service（从filePath推导）
    public ChangeType changeType;         // ADDED, MODIFIED, DELETED
    public ChangeSignificance significance; // 变更重要程度
    public List<ChangedMethod> changedMethods;

    public ChangedFile() {
        this.changedMethods = new ArrayList<>();
    }

    public ChangedFile(String filePath, String className, ChangeType changeType) {
        this.filePath = filePath;
        this.className = className;
        this.changeType = changeType;
        this.significance = ChangeSignificance.HIGH;
        this.changedMethods = new ArrayList<>();
    }
}
