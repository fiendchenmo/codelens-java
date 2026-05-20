// SYNC_SOURCE: src/main/java/com/codelens/JavaParserStructExtractor.java (CLI端实现)
// SYNC_VERSION: 2026-05-20-v1
// 维护方：喵呜（CLI端）
// 说明：代码结构底图数据，CLI端用JavaParser提取，插件端用PSI提取，输出格式统一

package com.codelens.common.normalizers;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码结构底图数据 - Layer 1
 * 由 JavaParser(CLI) 或 PSI(插件端) 提取，注入 LLM prompt
 */
public class StructContext {

    /** 字段信息 */
    public static class FieldInfo {
        public String name;       // 字段名，如 "queryRefBillService"
        public String type;       // 类型名，如 "IQueryRefBillService"
        public int line;          // 声明行号
        public String injection;  // 注入方式，如 "@Autowired" / "@Resource" / ""

        public FieldInfo(String name, String type, int line, String injection) {
            this.name = name;
            this.type = type;
            this.line = line;
            this.injection = injection;
        }
    }

    /** 方法信息 */
    public static class MethodInfo {
        public String signature;  // 方法签名，如 "saveBillForDraft(EcsBillDataMergeBO, UserInfo)"
        public int line;          // 声明行号
        public String visibility; // public/private/protected

        public MethodInfo(String signature, int line, String visibility) {
            this.signature = signature;
            this.line = line;
            this.visibility = visibility;
        }
    }

    private List<FieldInfo> fields = new ArrayList<>();
    private List<MethodInfo> methods = new ArrayList<>();
    private String packageName;
    private String className;

    public List<FieldInfo> getFields() { return fields; }
    public List<MethodInfo> getMethods() { return methods; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    /**
     * 格式化为 prompt 可读的结构化上下文
     */
    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("[代码结构底图 - 以下为代码真实提取，LLM必须以此为基准，不得自行推测]\n");
        sb.append("包名: ").append(packageName).append("\n");
        sb.append("类名: ").append(className).append("\n");

        sb.append("字段（依赖注入）:\n");
        for (FieldInfo f : fields) {
            sb.append("L").append(f.line).append(" | ");
            sb.append(f.type).append(" ").append(f.name);
            if (!f.injection.isEmpty()) {
                sb.append(" (").append(f.injection).append(")");
            }
            sb.append("\n");
        }

        sb.append("方法:\n");
        for (MethodInfo m : methods) {
            sb.append("L").append(m.line).append(" | ");
            sb.append(m.visibility).append(" ");
            sb.append(m.signature).append("\n");
        }

        return sb.toString();
    }
}
