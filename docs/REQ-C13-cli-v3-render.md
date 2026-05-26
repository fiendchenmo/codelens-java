# REQ-C13: CLI formatAnalysisResult 支持 V3 Schema 渲染

## 背景

V3 Schema (`--schema=v3`) 的 LLM 输出已正确产出 JSON，但 CLI 的 `formatAnalysisResult()` 只处理 V2 字段（dependencies/keyMethods/risks/framework_integration），导致 V3 输出只显示"概要:"，其余字段（fields/methods/framework）被静默忽略。

## 需求

修改 `CodeLensCli.formatAnalysisResult()` 方法，增加 V3 Schema 字段的渲染逻辑。

### V3 字段渲染规格

| V3 字段 | 渲染方式 | 参考V2对应 |
|---------|---------|-----------|
| `summary` | 已有（"概要:"）| 同V2 |
| `framework` | 新增（"━━━ 框架集成 ━━━"）| 对应V2的 framework_integration |
| `fields` | 新增（"━━━ 字段 ━━━"）| 对应V2的 dependencies 中的字段注入 |
| `methods` | 新增（"━━━ 方法 ━━━"）| 对应V2的 keyMethods |
| `methods[].calls` | 新增（方法内缩进显示调用列表）| 对应V2的 keyMethods[].calls |
| `methods[].risks` | 新增（方法内缩进显示风险）| 对应V2的顶层 risks |

### 渲染示例（V3 输出）

```
概要: RuoYi框架用户管理服务实现类，提供用户增删改查及密码重置功能

━━━ 框架集成 ━━━
  Spring框架集成，MyBatis Mapper操作数据库

━━━ 字段 ━━━
  * userMapper (SysUserMapper) [AUTOWIRED] L17 - 用户数据访问
  * userRoleMapper (SysUserRoleMapper) [AUTOWIRED] L20 - 用户角色数据访问

━━━ 方法 ━━━
  * selectUserList (L23, public, LOW) - 查询用户列表
    调用: userMapper.selectUserList() [L25, same_file]
  * insertUser (L37, public, MEDIUM) - 新增用户
    调用: SecurityUtils.encryptPassword() [L39, static]
          userMapper.insertUser() [L40, cross_file]
          insertUserRole() [L41, same_file]
    ⚠ MEDIUM: 密码加密后未校验强度 (L39)
  * resetPwd (L70, public, LOW) - 重置密码
    调用: SecurityUtils.encryptPassword() [L75, static]
          userMapper.updateUser() [L76, cross_file]
```

### 实现要点

1. **先检测Schema版本**：根据 JSON 中是否含 `methods`（V3）或 `keyMethods`（V2）判断版本，走不同渲染分支
2. **V2 渲染逻辑不动**：保持现有 dependencies/keyMethods/risks 的渲染不变
3. **V3 渲染新增**：
   - `fields[]`：name, type, injectType, line, description
   - `methods[]`：name, line, complexity, visibility, description, calls, risks
   - `framework`：直接输出文本
4. **保持 ColorUtil 一致**：使用现有颜色方案（heading/info/warning）
5. **兼容性**：V2 字段（如 summary）在两种模式下都渲染

### 文件改动范围

- `codelens-cli/src/main/java/com/codelens/CodeLensCli.java` — formatAnalysisResult() 方法

### 验收标准

1. `--schema=v3` 模式下 CLI 显示完整的 fields/methods/framework 信息
2. 默认 V2 模式下显示不变（无回归）
3. `mvn test -pl codelens-cli` 全过
4. 手动测试：`java -jar codelens.jar analyze SysUserServiceImpl.java --schema=v3` 显示完整 V3 输出
