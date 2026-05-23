# 需求 — SchemaVersion 枚举 + JSON_SCHEMA 版本化

> 编号：REQ-C3
> 优先级：🟡 P1
> 工作量：1d
> 前置依赖：C-1（移除 architecture_issues 规则，清理旧 Schema 中的冗余字段）
> 责任人：喵呜
> 交付日期：5/27
> 变更归属：🟠 common变更 + 📋 Schema责任人

## 目的

当前 JSON_SCHEMA 只有一个版本，CLI 端和插件端共用同一套 Schema。随着 V3 功能引入（[FACT]/[INFER] 标注、calls 数组、方法级行号），需要支持多版本 Schema 并存，让插件端先行切 V3 而 CLI 端保持 V2 兼容。

目标：
1. 引入 SchemaVersion 枚举，标识 V2/V3
2. CodeMetaData 中的 JSON_SCHEMA 根据 version 动态生成
3. 下游组件（SystemPrompt、Normalizer）按 version 选择对应逻辑分支

## 设计方案

### SchemaVersion 枚举

```java
public enum SchemaVersion {
    V2("v2"),
    V3("v3");
    
    private final String label;
    // constructor, getter
}
```

### CodeMetaData 改造

```java
// 当前：硬编码单一 schema
public static final String JSON_SCHEMA = "...";

// 改为：按 version 构建
public static String buildSchema(SchemaVersion version) {
    Map<String, Object> schema = new LinkedHashMap<>();
    // 公共字段
    schema.put("keyMethods", ...);
    schema.put("dependencies", ...);
    
    // V3 特有字段
    if (version == SchemaVersion.V3) {
        schema.put("calls", ...);       // calls 数组（V3 从 dependencies 中拆出）
        schema.put("facts", ...);       // [FACT] 标注项
        schema.put("inferences", ...);  // [INFER] 标注项
    }
    
    return toJson(schema);
}
```

### 版本差异对照

| 字段 | V2 | V3 |
|------|----|----|
| keyMethods | ✅ | ✅ |
| dependencies（含 method_call） | ✅ | ❌（method_call 移入 calls） |
| dependencies（仅 field 类型） | ❌ | ✅ |
| calls（keyMethods 内嵌） | ❌ | ✅ |
| architecture_issues | ✅（可选） | ❌（C-1 已移除） |
| [FACT]/[INFER] 标注 | ❌ | ✅ |

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | 新增 SchemaVersion 枚举 |
| codelens-common | CodeMetaData: buildSchema(SchemaVersion) 替代硬编码 JSON_SCHEMA |
| codelens-common | 保留旧 JSON_SCHEMA 常量标记 @Deprecated，过渡期兼容 |

## 验收标准

- [ ] SchemaVersion 枚举在 common 模块中
- [ ] buildSchema(V2) 输出与当前硬编码 JSON_SCHEMA 结构一致
- [ ] buildSchema(V3) 输出含 calls 数组、[FACT]/[INFER] 字段，不含 architecture_issues
- [ ] 旧 JSON_SCHEMA 常量标记 @Deprecated 但仍可用
- [ ] `mvn test` 全部通过
- [ ] JDK 1.8 语法

## 约束

- C-1 完成后再开发（V3 Schema 不含 architecture_issues）
- 过渡期保持向后兼容，旧代码引用 JSON_SCHEMA 不报错
- JDK 1.8 语法
