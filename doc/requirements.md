# C语言结构体解析工具需求文档

## 1. 项目概述

### 1.1 项目背景
开发一个自定义的结构体/联合体解析工具，语法参考C语言，但针对嵌入式/硬件寄存器描述场景进行简化定制。

### 1.2 目标用户
- 嵌入式系统开发者
- 硬件寄存器描述工具开发者
- 需要自定义二进制数据格式描述的用户

---

## 2. 功能需求

### 2.1 核心功能

#### 2.1.1 结构体定义解析
- 支持 `struct` 关键字定义结构体
- 支持结构体嵌套（结构体内包含其他结构体）
- 支持匿名结构体（无标签名的结构体）

#### 2.1.2 联合体定义解析
- 支持 `union` 关键字定义联合体
- 支持联合体嵌套（联合体内包含结构体或其他联合体）
- 支持匿名联合体（无标签名的联合体）

#### 2.1.3 数据类型支持
- **仅支持无符号整数类型**：`uint1` ~ `uint32`
- `uintN` 代表 N bit 无符号整数（1 ≤ N ≤ 32）
- **不支持**：signed类型、float、double、char、指针等C语言标准类型

#### 2.1.4 位域支持
- 所有字段均为位域形式
- 无需显式声明 `: n`，类型本身即表示位宽
- **无需字节对齐**，字段按声明顺序紧密排列

### 2.2 语法规范

#### 2.2.1 基本语法
```c
// 结构体定义
struct StructName {
    uint8  field1;    // 8bit 无符号整数
    uint16 field2;    // 16bit 无符号整数
    uint1  flag;      // 1bit 标志位
};

// 联合体定义
union UnionName {
    uint32 raw;       // 32bit 原始值
    struct {
        uint16 low;
        uint16 high;
    } split;
};
```

#### 2.2.2 嵌套定义
```c
struct Outer {
    uint8 header;
    struct {              // 匿名结构体
        uint8 a;
        uint8 b;
    } inner;
    union {               // 匿名联合体
        uint16 word;
        struct {
            uint8 low;
            uint8 high;
        } bytes;
    } data;
};
```

#### 2.2.3 类型别名（typedef）
```c
// 为结构体/联合体创建别名
typedef struct {
    uint32 addr;
    uint32 value;
} RegPair;

// 使用别名
struct Container {
    RegPair reg;
    uint8   valid;
};
```

### 2.2.4 跨文件结构体引用
支持引用其他头文件中定义的结构体/联合体：
```c
// types.h
struct Reg32 {
    uint32 value;
};

// device.h
#include "types.h"

struct DataReg {
    struct Reg32 data;    // 引用 types.h 中定义的 Reg32
    uint8 valid;
};
```

### 2.3 预处理指令支持
#### 2.3.1 #include 支持
- 支持 `#include "file.h"` 形式（优先在当前目录查找）
- 支持 `#include <file.h>` 形式（在搜索路径中查找）
- 支持通过命令行 `-I` 选项添加搜索路径
- 自动处理循环引用（通过文件去重机制）
- **GCC 预处理不保留注释**（使用 `-E -P` 参数，简化输出）

#### 2.3.2 其他预处理指令
- `#define`、`#ifndef`、`#endif` 等指令会被 GCC 预处理
- 但不进行宏展开处理

### 2.4 注释支持
- 支持C风格单行注释：`// 注释内容`
- 支持C风格多行注释：`/* 注释内容 */`
- **注意**：注释在 GCC 预处理阶段被移除，不会传递给解析器

### 2.5 交叉引用限制
**不支持交叉引用和前向引用**。当检测到以下情况时会报错：
- **自引用**：结构体/联合体引用自身
- **双向交叉引用**：A 引用 B，B 又引用 A
- **多向循环引用**：A → B → C → A
- **前向引用**：引用尚未定义的类型

**错误示例：**
```c
// ❌ 不允许：交叉引用
struct NodeA {
    uint8 value;
    NodeB next;  // 错误：NodeB 未定义
};

struct NodeB {
    uint16 data;
    NodeA prev;  // 错误：交叉引用
};
```

**正确示例：**
```c
// ✅ 允许：先定义后引用
struct Inner {
    uint8 a;
    uint8 b;
};

struct Outer {
    uint8 header;
    Inner inner;  // 正确：Inner 已定义
    uint8 footer;
};
```

---

## 3. 输出功能

### 3.1 解析结果输出
- 输出结构体/联合体的完整定义信息
- 包含每个字段的：名称、类型、位宽、偏移量（bit级别）
- 包含每个结构体/联合体的：总大小（bit）

### 3.2 序列化支持（可选）
- 生成JSON/XML格式的结构描述
- 支持代码生成（如生成C结构体、Python类、Rust结构体等）

---

## 4. 非功能需求

### 4.1 性能要求
- 解析千行级别头文件应在1秒内完成
- 内存占用与文件大小成线性关系
- ANTLR4 的预测解析（LL(*)）保证高效解析

### 4.2 错误处理
- 提供清晰的错误信息，包含行号和列号
- 检测常见错误：
  - 未定义的类型
  - 位宽超出范围（>32）
  - 结构体/联合体未闭合
  - 重复的字段名
  - 重复的结构体/联合体标签名
- ANTLR4 自动提供语法错误恢复和报告机制

### 4.3 扩展性
- 预留扩展接口，便于后续添加新特性
- 模块化设计，便于维护和测试

---

## 5. 输入输出示例

### 5.1 输入示例
```c
// 寄存器定义示例
struct ControlReg {
    uint1  enable;
    uint1  interrupt;
    uint2  mode;
    uint4  reserved;
    uint8  prescale;
    uint16 timeout;
};
```

### 5.2 输出示例（JSON格式）
```json
{
  "structs": [{
    "name": "ControlReg",
    "type": "struct",
    "size_bits": 32,
    "fields": [
      {"name": "enable",     "type": "uint1",  "bits": 1,  "offset": 0},
      {"name": "interrupt",  "type": "uint1",  "bits": 1,  "offset": 1},
      {"name": "mode",       "type": "uint2",  "bits": 2,  "offset": 2},
      {"name": "reserved",   "type": "uint4",  "bits": 4,  "offset": 4},
      {"name": "prescale",   "type": "uint8",  "bits": 8,  "offset": 8},
      {"name": "timeout",    "type": "uint16", "bits": 16, "offset": 16}
    ]
  }],
  "unions": []
}
```

### 5.3 多文件解析示例

**types.h:**
```c
struct Reg32 {
    uint32 value;
};
```

**device.h:**
```c
#include "types.h"

struct Device {
    struct Reg32 data;    // 引用 types.h 中的结构体
    uint8 valid;
};
```

**命令行使用:**
```bash
# 解析单个文件
java -jar struct-parser.jar parse device.h

# 解析并指定搜索路径
java -jar struct-parser.jar parse device.h -I ./include -I /usr/local/include
```

---

## 6. 边界条件

### 6.1 位宽限制
- 单个字段最大32bit
- 结构体/联合体总大小无硬性限制（受内存限制）

### 6.2 命名规则
- 标识符支持字母、数字、下划线
- 不能以数字开头
- 区分大小写

### 6.3 特殊场景
- 空结构体/联合体：大小为0bit
- 位宽为0的字段：不支持
- 柔性数组：不支持

---

## 7. 参考实现

### 7.1 技术栈
- **语言**：Java 26
- **解析器生成器**：ANTLR4
- **构建工具**：Maven
- **测试框架**：JUnit 5（112+ 单元测试）
- **JDK特性**：Record类、Switch Expressions、Pattern Matching、var类型推断
- **预处理**：GCC（`gcc -E -P`，不保留注释）

### 7.2 ANTLR4 实现要点
- **词法规则（Lexer）**：定义关键字、标识符、数字、注释等token
- **语法规则（Parser）**：定义struct/union的递归语法
- **访问者模式（Visitor）**：遍历AST生成解析结果
- **两遍扫描策略**：
  - 第一遍：收集所有顶层结构体和联合体的名称
  - 第二遍：解析字段并检测交叉引用
- **监听器模式（Listener）**：可选，用于特定场景的事件处理

### 7.3 类似项目参考
- C语言标准：ISO/IEC 9899
- 寄存器描述工具：SVD (System View Description)
- 协议描述语言：Protocol Buffers

### 7.4 项目结构
```
src/
├── main/
│   ├── antlr4/
│   │   └── StructParser.g4          # ANTLR4语法定义文件
│   ├── java/
│   │   ├── parser/
│   │   │   ├── StructParserService.java   # 解析服务入口
│   │   │   ├── StructParseVisitor.java    # 自定义Visitor实现（两遍扫描）
│   │   │   ├── GccPreprocessor.java       # GCC预处理器（不保留注释）
│   │   │   └── HeaderFileLoader.java      # 头文件加载器（支持#include）
│   │   ├── model/
│   │   │   ├── Struct.java          # 结构体模型（Record）
│   │   │   ├── Union.java           # 联合体模型（Record）
│   │   │   ├── Field.java           # 字段模型（Record）
│   │   │   ├── Type.java            # 类型定义
│   │   │   └── ParseResult.java     # 解析结果封装（Record）
│   │   └── generator/
│   │       └── JsonGenerator.java   # JSON输出生成器
│   └── resources/
│       └── include/                 # 示例头文件
│           ├── base_types.h         # 基础类型定义
│           └── device_types.h       # 设备类型（引用基础类型）
└── test/
    ├── java/
    │   └── parser/
    │       ├── StructParserServiceTest.java   # 单元测试
    │       ├── MultiFileParserTest.java       # 多文件解析测试
    │       ├── MultiFileReferenceTest.java    # 跨文件引用测试
    │       └── CircularReferenceTest.java     # 交叉引用检测测试
    └── resources/
        └── headers/                 # 测试用头文件
            ├── types.h
            ├── device.h
            ├── circular_a.h         # 循环引用测试
            ├── circular_b.h
            └── include/
                └── common.h
```

---

## 8. 版本规划

### v1.0 - MVP版本（已完成）
- [x] ANTLR4 语法定义文件（StructParser.g4）
- [x] 基础struct/union解析
- [x] uint1~uint32类型支持
- [x] 嵌套支持
- [x] 匿名struct/union支持
- [x] Visitor模式实现AST遍历
- [x] JSON输出
- [x] JDK 26升级（Record类、Switch Expressions等）

### v1.1 - 多文件解析版本（已完成）
- [x] #include 预处理支持
- [x] 头文件搜索路径机制
- [x] 跨文件结构体引用解析
- [x] 循环引用检测与处理
- [x] 命令行 `-I` 选项支持

### v1.2 - 当前版本（已完成）
- [x] **交叉引用检测**：禁止结构体/联合体的交叉引用和前向引用
- [x] **多文件引用场景**：支持 b.h #include a.h 并引用其中的类型
- [x] **GCC 预处理优化**：不再保留注释，简化输出（`gcc -E -P`）
- [x] **完善的测试覆盖**：112+ 单元测试，覆盖各种边界场景
- [x] **两遍扫描策略**：第一遍收集名称，第二遍解析并检测交叉引用

### v1.3 - 规划中
- [ ] typedef完整支持
- [ ] 数组类型支持（如 `uint8 data[4]`）
- [ ] 代码生成（C/Python/Rust）
- [ ] 位字段对齐选项

### v2.0 - 高级版本（规划中）
- [ ] 条件编译（#ifdef风格）
- [ ] 宏定义支持
- [ ] IDE插件支持
- [ ] LSP协议支持

---

## 9. 附录

### 9.1 术语表
| 术语 | 说明 |
|------|------|
| 位域 | Bit-field，C语言中指定字段占用bit数的功能 |
| 匿名结构体 | 没有标签名的结构体，通常用于嵌套 |
| 联合体 | Union，所有成员共享同一内存空间 |
| 字节对齐 | 数据按特定字节边界排列，本工具**不支持** |
| 前向引用 | 引用在文件中后定义的结构体，本工具**暂不支持** |
| Record类 | JDK 16+ 特性，用于创建不可变数据类 |
| Include Guard | 通过 `#ifndef` 防止头文件重复包含的机制 |

### 9.2 修订记录
| 版本 | 日期 | 修订内容 | 作者 |
|------|------|----------|------|
| v1.0 | 2026-04-18 | 初始版本 | - |
| v1.1 | 2026-04-18 | 升级JDK 26，使用Record重构 | - |
| v1.2 | 2026-04-18 | 添加多文件解析和#include支持 | - |
| v1.3 | 2026-04-19 | 添加交叉引用检测、多文件引用场景、GCC预处理优化 | - |
