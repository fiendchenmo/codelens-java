# C-6 V2/V3 字段级对比报告 (C9 SysUserServiceImpl)

> 测试日期：2026-05-26
> 用例：C9 SysUserServiceImpl (7个业务方法, 2个字段注入)

---

## 一、顶层结构对比

| 维度 | V2 | V3 |
|------|----|----|
| 顶层字段数 | 7 | 4 |
| 顶层字段 | summary, design_intent, class_analysis, dependencies, risks, keyMethods, framework_integration | summary, framework, fields, methods |
| JSON体积 | 5,792 bytes | 5,790 bytes |
| 结构特点 | 扁平化，risks/deps在顶层 | 嵌套化，risks在methods内 |

## 二、字段映射验证

### 2.1 dependencies → fields ✅

| V2 dependencies | V3 fields | 映射 |
|---|---|---|
| userMapper (field) L17 | userMapper (SysUserMapper) [AUTOWIRED] L17 | ✅ 完整映射 |
| userRoleMapper (field) L20 | userRoleMapper (SysUserRoleMapper) [AUTOWIRED] L20 | ✅ 完整映射 |

V3 fields 比 V2 dependencies 多了 type 和 injectType 信息，更精确。

### 2.2 keyMethods → methods ✅

| 方法名 | V2 keyMethods | V3 methods | calls数 | 映射 |
|---|---|---|---|---|
| selectUserList | L23 | L23, public, LOW | 1/1 | ✅ |
| selectUserByUserName | L28 | L28, public, LOW | 1/1 | ✅ |
| insertUser | L37 | L37, public, MEDIUM | 4/3 | ✅ (V2含1个依赖注入) |
| updateUser | L45 | L45, public, MEDIUM | 3/3 | ✅ |
| deleteUserByIds | L52 | L52, public, LOW | 2/2 | ✅ |
| insertUserRole | L58 | L58, private, LOW | 1/1 | ✅ |
| resetPwd | L70 | L70, public, LOW | 2/2 | ✅ |

7/7 方法全部映射正确，行号一致。

### 2.3 calls 格式 ✅

| | V2 | V3 |
|---|---|---|
| 格式 | 对象 {method, line, type} | 对象 {target, line, type} |
| 字段名 | method | target |
| CLI兼容 | — | ✅ getStringField("target", fallback "method") |

V3 Schema 定义 `target`，LLM遵循输出 `target`，CLI已兼容。

### 2.4 risks 位置 ⚠️

| | V2 (顶层) | V3 (methods内) |
|---|---|---|
| 总数 | 3 | 1 |
| HIGH | 1 (@Transactional缺失) | 1 (同) |
| MEDIUM | 2 (循环+权限) | 0 ⚠️ |

**V3遗漏2个风险**：insertUserRole循环风险、resetPwd权限校验风险。
V3 [FACT]/[INFER]协议可能使模型更保守，只在高确信度时标注风险。

### 2.5 V3独有字段覆盖率

| 字段 | 覆盖率 |
|---|---|
| logic_summary | 7/7 ✅ |
| params | 7/7 ✅ |
| return | 7/7 ✅ |
| exceptions | 2/7 (仅2个方法有) |
| called_by | 1/7 |

## 三、信息量对比

| 信息类别 | V2 | V3 | 结论 |
|---|---|---|---|
| 字段注入 | 2项(名称+类型) | 2项(+injectType) | V3更丰富 |
| 方法列表 | 7项 | 7项 | 一致 |
| 调用关系 | 字符串列表 | 对象数组(含line+type) | V3更结构化 |
| 风险发现 | 3个 | 1个 | V2更全面 ⚠️ |
| 框架分析 | 有(framework_integration) | 有(framework) | 等价 |
| 设计意图 | 有(design_intent) | 融入summary | V3更紧凑 |
| 数据流 | 有(class_analysis) | 融入summary | 信息量减少 |
| 参数描述 | 无 | 有(params) | V3独有 ✅ |
| 逻辑摘要 | 无 | 有(logic_summary) | V3独有 ✅ |
| 返回值含义 | 无 | 有(return.business_meaning) | V3独有 ✅ |

## 四、结论

### 通过项 ✅
1. V3 JSON格式正确，字段映射完整
2. dependencies → fields 映射正确，V3信息更丰富
3. keyMethods → methods 映射正确，行号一致
4. calls 对象格式正确，字段名兼容处理OK
5. V3独有字段(params/logic_summary/return)覆盖率100%

### 待改进 ⚠️
1. **V3 risks遗漏** — 仅识别1/3风险，需评估是否为prompt保守化副作用
2. **class_analysis信息丢失** — V2有637字数据流描述，V3融入summary后信息量减少
3. **called_by覆盖率低** — 1/7，可能是Flash模型对调用方分析能力有限

### 建议
1. V3 prompt可微调，鼓励模型在[INFER]阶段积极标注风险
2. 考虑在V3 Schema中增加顶层 risks 数组（与methods内risks并存）兜底
3. class_analysis 可考虑作为V3 summary的补充字段
