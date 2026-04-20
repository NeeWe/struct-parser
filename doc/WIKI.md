# Struct Parser Wiki

## 项目概述

Struct Parser 是一个 C 语言风格结构体/联合体解析工具，专为嵌入式/硬件寄存器描述场景设计。

## 快速开始

### 安装

```bash
# 克隆仓库
git clone https://github.com/NeeWe/struct-parser.git
cd struct-parser

# 编译
mvn clean package

# 运行
java -jar target/struct-parser-1.0.0-jar-with-dependencies.jar
```

### 配置

创建 `struct-parser.yaml`：

```yaml
compileConfigFile: ./command.txt
output:
  format: json
  outputFile: output.json
```

创建编译配置文件 `command.txt`：

```txt
gcc -E -P -I./include -I./drivers
```

### 运行

```bash
# 解析（无参数，自动读取 struct-parser.yaml）
java -jar struct-parser.jar

# 检查 GCC 可用性
java -jar struct-parser.jar gcc-info

# 查看帮助
java -jar struct-parser.jar help
```

## 支持的语法

### 结构体

```c
struct ControlReg {
    uint1  enable;
    uint1  interrupt;
    uint2  mode;
    uint4  reserved;
    uint8  prescale;
    uint16 timeout;
};
```

### 联合体

```c
union DataUnion {
    uint32 raw;
    struct {
        uint16 low;
        uint16 high;
    } split;
};
```

### 嵌套定义

```c
struct Outer {
    uint8 header;
    struct {
        uint8 a;
        uint8 b;
    } inner;
    union {
        uint16 word;
        struct {
            uint8 low;
            uint8 high;
        } bytes;
    } data;
};
```

### 类型别名

```c
typedef struct {
    uint32 addr;
    uint32 value;
} RegPair;

struct Container {
    RegPair reg;
    uint8   valid;
};
```

### 跨文件引用

**base_types.h：**
```c
#ifndef BASE_TYPES_H
#define BASE_TYPES_H

struct Status {
    uint8 code;
    uint8 flags;
};

union ConfigValue {
    uint32 raw;
    struct {
        uint16 low;
        uint16 high;
    } parts;
};

#endif // BASE_TYPES_H
```

**device_types.h：**
```c
#ifndef DEVICE_TYPES_H
#define DEVICE_TYPES_H

#include "base_types.h"

struct DeviceInfo {
    Status status;      // 引用 base_types.h 中的 Status
    uint32 device_id;
};

struct DeviceConfig {
    uint8 mode;
    ConfigValue config; // 引用 base_types.h 中的 ConfigValue
    uint16 timeout;
};

#endif // DEVICE_TYPES_H
```

**注意**：被引用的类型必须先定义，不支持前向引用和交叉引用。

## 数据类型

| 类型 | 位宽 |
|------|------|
| uint1 | 1 bit |
| uint2 ~ uint7 | 2-7 bits |
| uint8 | 8 bits |
| uint9 ~ uint15 | 9-15 bits |
| uint16 | 16 bits |
| uint17 ~ uint31 | 17-31 bits |
| uint32 | 32 bits |

**注意**：仅支持 uint1~uint32，不支持 signed、float、char、指针等类型。

## 输出格式

### JSON 输出示例

#### 基础结构体

```json
{
  "structs": [{
    "name": "",
    "type": "ControlReg",
    "bits": 32,
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

#### 嵌套结构体/联合体

当 struct 包含嵌套的 struct 或 union 时，嵌套的 fields 会作为子数组包含在字段中：

**匿名嵌套：**

```json
{
  "structs": [{
    "name": "",
    "type": "Outer",
    "bits": 40,
    "fields": [
      {"name": "header", "type": "uint8", "bits": 8, "offset": 0},
      {
        "name": "inner",
        "type": "",
        "bits": 16,
        "offset": 8,
        "fields": [
          {"name": "a", "type": "uint8", "bits": 8, "offset": 0},
          {"name": "b", "type": "uint8", "bits": 8, "offset": 8}
        ]
      }
    ]
  }],
  "unions": []
}
```

**具名引用（保留嵌套结构）：**

```c
struct Point {
    uint16 x;
    uint16 y;
};

struct Rectangle {
    struct Point topLeft;
    struct Point bottomRight;
};
```

```json
{
  "structs": [
    {
      "name": "",
      "type": "Point",
      "bits": 32,
      "fields": [
        {"name": "x", "type": "uint16", "bits": 16, "offset": 0},
        {"name": "y", "type": "uint16", "bits": 16, "offset": 16}
      ]
    },
    {
      "name": "",
      "type": "Rectangle",
      "bits": 64,
      "fields": [
        {
          "name": "topLeft",
          "type": "Point",
          "bits": 32,
          "offset": 0,
          "fields": [
            {"name": "x", "type": "uint16", "bits": 16, "offset": 0},
            {"name": "y", "type": "uint16", "bits": 16, "offset": 16}
          ]
        },
        {
          "name": "bottomRight",
          "type": "Point",
          "bits": 32,
          "offset": 32,
          "fields": [
            {"name": "x", "type": "uint16", "bits": 16, "offset": 0},
            {"name": "y", "type": "uint16", "bits": 16, "offset": 16}
          ]
        }
      ]
    }
  ],
  "unions": []
}
```

**Type 字段规则**：
- **顶层结构/联合体**：`name` 为空字符串 `""`，`type` 为名称（如 `"Point"`）
- **匿名嵌套**：`type` 为空字符串 `""`
- **具名引用**：`type` 为具体的结构体/联合体名称（如 `"Point"`）

## 配置详解

### compileConfigFile

指定编译配置文件路径，该文件包含 gcc 预处理命令：

```yaml
compileConfigFile: ./command.txt
```

**编译配置文件格式**（直接命令文件）：

```txt
gcc -E -P -I./include -I./drivers -nostdinc
```

**注意**：仅支持直接命令文件格式，不支持 JSON Compilation Database 或 Makefile。

### output

配置输出选项：

```yaml
output:
  format: json                    # 输出格式（目前仅支持 json）
  outputFile: output/result.json  # 输出文件路径（可选，默认 stdout）
```

## 工作原理

1. **配置加载**：读取 `struct-parser.yaml` 并加载编译配置
2. **头文件扫描**：从编译配置文件所在目录扫描头文件
3. **GCC 预处理**：使用编译配置文件中的 gcc 命令进行预处理（不保留注释）
4. **两遍扫描解析**：
   - 第一遍：收集所有顶层结构体和联合体的名称
   - 第二遍：解析字段并检测交叉引用
5. **交叉引用检测**：检查自引用、双向和多向循环引用
6. **结果合并**：合并所有头文件的解析结果
7. **JSON 输出**：生成 JSON 格式的结构描述

## 技术栈

- **Java 26**：Record 类、Switch Expressions、Pattern Matching
- **ANTLR4**：语法解析器生成器
- **Maven**：构建工具
- **JUnit 5**：测试框架
- **Jackson**：JSON/YAML 处理

## 项目结构

```
struct-parser/
├── doc/
│   ├── requirements.md    # 需求文档
│   └── WIKI.md           # 项目 Wiki
├── src/
│   ├── main/
│   │   ├── antlr4/       # ANTLR4 语法文件
│   │   ├── java/         # Java 源码
│   │   │   ├── config/   # 配置类
│   │   │   ├── generator/# 代码生成器
│   │   │   ├── model/    # 数据模型
│   │   │   └── parser/   # 解析器实现
│   │   └── resources/    # 资源文件
│   └── test/             # 测试代码
├── struct-parser.yaml    # 示例配置
├── pom.xml              # Maven 配置
└── README.md            # 项目说明
```

## 常见问题

### Q: 为什么必须安装 GCC？

A: GCC 用于预处理头文件，处理 `#include`、`#define`、`#ifdef` 等指令，确保解析的是标准 C 预处理后的代码。

### Q: 支持哪些预处理指令？

A: 支持标准 C 预处理指令，包括 `#include`、`#define`、`#ifdef`、`#ifndef`、`#endif` 等。

### Q: GCC 预处理是否会保留注释？

A: **不会**。GCC 预处理时使用 `-E -P` 参数，不保留注释。这样可以：
- 简化预处理输出
- 减少不必要的注释内容
- 提高解析效率，避免注释干扰

### Q: 如何处理循环引用？

A: **交叉引用被视为错误行为**。工具实现了两遍扫描策略来检测：
- **自引用**：结构体/联合体引用自身
- **双向交叉引用**：A 引用 B，B 又引用 A
- **多向循环引用**：A → B → C → A

当检测到交叉引用时，会报告 "Circular reference detected" 或 "Forward reference not allowed" 错误。

**示例（错误）：**
```c
// ❌ 不允许：交叉引用
struct NodeA {
    uint8 value;
    NodeB next;  // 错误：前向引用
};

struct NodeB {
    uint16 data;
    NodeA prev;  // 错误：交叉引用
};
```

**示例（正确）：**
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

### Q: 是否支持数组类型？

A: 当前版本（v1.1）不支持数组类型，计划在 v1.2 版本添加支持。

### Q: 是否支持前向引用？

A: 当前版本（v1.1）不支持前向引用（引用后定义的结构体），计划在 v1.2 版本添加支持。

## 版本历史

### v1.0 - MVP 版本
- ANTLR4 语法定义
- 基础 struct/union 解析
- uint1~uint32 类型支持
- 嵌套支持
- 匿名 struct/union 支持
- JSON 输出

### v1.1 - 多文件解析版本
- GCC 预处理集成
- #include 支持
- 配置驱动（YAML/JSON）
- 头文件搜索路径
- 跨文件结构体引用
- JDK 26 + Record 类重构

### v1.2 - 当前版本（最新）
- **交叉引用检测**：禁止结构体/联合体的交叉引用和前向引用
- **多文件引用场景**：支持 b.h #include a.h 并引用其中的类型
- **GCC 预处理优化**：不再保留注释，简化输出
- **完善的测试覆盖**：112+ 单元测试，覆盖各种边界场景

### v1.3 - 规划中
- typedef 完整支持
- 数组类型支持
- 代码生成（C/Python/Rust）
- 位字段对齐选项

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License
