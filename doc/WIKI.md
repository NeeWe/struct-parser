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
includePaths:
  - ./include
  - ./drivers
gccCommand: gcc
gccRequired: true
output:
  format: json
  outputFile: output.json
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
    "name": "ControlReg",
    "type": "struct",
    "size_bits": 32,
    "anonymous": false,
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
    "name": "Outer",
    "type": "struct",
    "size_bits": 40,
    "anonymous": false,
    "fields": [
      {"name": "header", "type": "uint8", "bits": 8, "offset": 0},
      {
        "name": "inner",
        "type": "struct",
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
      "name": "Point",
      "type": "struct",
      "size_bits": 32,
      "anonymous": false,
      "fields": [
        {"name": "x", "type": "uint16", "bits": 16, "offset": 0},
        {"name": "y", "type": "uint16", "bits": 16, "offset": 16}
      ]
    },
    {
      "name": "Rectangle",
      "type": "struct",
      "size_bits": 64,
      "anonymous": false,
      "fields": [
        {
          "name": "topLeft",
          "type": "struct",
          "bits": 32,
          "offset": 0,
          "fields": [
            {"name": "x", "type": "uint16", "bits": 16, "offset": 0},
            {"name": "y", "type": "uint16", "bits": 16, "offset": 16}
          ]
        },
        {
          "name": "bottomRight",
          "type": "struct",
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

## 配置详解

### includePaths

指定要扫描的头文件目录（不递归子目录）：

```yaml
includePaths:
  - ./include      # 扫描 ./include/ 下的 .h, .hpp 文件
  - ./drivers      # 扫描 ./drivers/ 下的 .h, .hpp 文件
```

### gccCommand

指定 GCC 命令，默认使用 `gcc`：

```yaml
gccCommand: gcc              # 系统默认 GCC
gccCommand: arm-none-eabi-gcc  # 交叉编译器
```

### gccRequired

强制使用 GCC 预处理，必须为 `true`：

```yaml
gccRequired: true
```

### output

配置输出选项：

```yaml
output:
  format: json                    # 输出格式（目前仅支持 json）
  outputFile: output/result.json  # 输出文件路径（可选，默认 stdout）
```

## 工作原理

1. **配置加载**：读取 `struct-parser.yaml`
2. **头文件扫描**：扫描 `includePaths` 中的 `.h`, `.hpp`, `.hh`, `.hxx` 文件
3. **GCC 预处理**：使用 `gcc -E` 进行标准 C 预处理
4. **ANTLR4 解析**：解析预处理后的代码
5. **结果合并**：合并所有头文件的解析结果
6. **JSON 输出**：生成 JSON 格式的结构描述

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

### Q: 如何处理循环引用？

A: GCC 预处理会自动处理 Include Guard（`#ifndef`/`#define`/`#endif`），工具会正确解析预处理后的结果。

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

### v1.1 - 多文件解析版本（当前）
- GCC 预处理集成
- #include 支持
- 配置驱动（YAML/JSON）
- 头文件搜索路径
- 跨文件结构体引用
- JDK 26 + Record 类重构

### v1.2 - 规划中
- typedef 完整支持
- 数组类型支持
- 前向引用支持
- 代码生成（C/Python/Rust）

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License
