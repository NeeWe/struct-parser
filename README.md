# Struct Parser

[![Java 26](https://img.shields.io/badge/Java-26-blue.svg)](https://openjdk.org/projects/jdk/26/)
[![ANTLR4](https://img.shields.io/badge/ANTLR-4.13.1-orange.svg)](https://www.antlr.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-green.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A C-style struct/union parser with ANTLR4, designed for embedded systems and hardware register description. Supports `#include` preprocessing, cross-file struct references, and generates JSON output with bit-level field layout.

## Features

- **Struct/Union Parsing**: Parse C-style struct and union definitions
- **Nested Types**: Support nested and anonymous struct/union
- **Custom Types**: uint1~uint32 data types with implicit bit-width
- **No Byte Alignment**: Fields are packed tightly (bit-level layout)
- **#include Support**: Preprocess header files with search paths
- **Cross-File References**: Reference structs defined in other headers
- **JSON Output**: Generate structured JSON with field offsets and sizes

## Quick Start

### Build

```bash
mvn clean package
```

### Run

```bash
# Parse a header file
java -jar target/struct-parser-1.0.0-jar-with-dependencies.jar parse input.h

# With include paths
java -jar target/struct-parser-1.0.0-jar-with-dependencies.jar parse input.h -I ./include -I /usr/local/include

# Run built-in example
java -jar target/struct-parser-1.0.0-jar-with-dependencies.jar example
```

## Example

### Input

```c
// Control register definition
struct ControlReg {
    uint1  enable;      // 1 bit
    uint1  interrupt;   // 1 bit
    uint2  mode;        // 2 bits
    uint4  reserved;    // 4 bits
    uint8  prescale;    // 8 bits
    uint16 timeout;     // 16 bits
};

// Data packet header with nested union
struct PacketHeader {
    uint8  version;
    uint8  type;
    uint16 length;
    union {
        uint32 raw;
        struct {
            uint16 low;
            uint16 high;
        } words;
    } checksum;
};
```

### Output

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

## Multi-File Support

Reference structs from other headers:

```c
// types.h
struct Reg32 {
    uint32 value;
};

// device.h
#include "types.h"

struct DataReg {
    struct Reg32 data;    // Reference struct from types.h
    uint8 valid;
};
```

Parse with include path:
```bash
java -jar struct-parser.jar parse device.h -I ./include
```

## Syntax

### Supported Types

| Type | Bits | Description |
|------|------|-------------|
| uint1  | 1  | Single bit flag |
| uint2~uint7 | 2-7 | Small integer |
| uint8  | 8  | Byte |
| uint9~uint15 | 9-15 | Extended byte |
| uint16 | 16 | Half word |
| uint17~uint31 | 17-31 | Extended half word |
| uint32 | 32 | Word |

### Struct Definition

```c
struct StructName {
    uint8  field1;
    uint16 field2;
    // ... more fields
};
```

### Union Definition

```c
union UnionName {
    uint32 raw;
    struct {
        uint16 low;
        uint16 high;
    } split;
};
```

### Anonymous Types

```c
struct Outer {
    uint8 header;
    struct {              // Anonymous struct with field name
        uint8 a;
        uint8 b;
    } inner;
    union {               // Anonymous union
        uint16 word;
        struct {
            uint8 low;
            uint8 high;
        } bytes;
    } data;
};
```

### Type Alias (typedef)

```c
typedef uint8 Byte;
typedef struct {
    uint32 addr;
    uint32 value;
} RegPair;
```

## Architecture

```
src/
├── main/antlr4/
│   └── StructParser.g4          # ANTLR4 grammar
├── main/java/
│   ├── parser/
│   │   ├── StructParserService.java   # Entry point
│   │   ├── StructParseVisitor.java    # AST visitor
│   │   └── HeaderFileLoader.java      # #include handler
│   ├── model/
│   │   ├── Struct.java          # Struct model (Record)
│   │   ├── Union.java           # Union model (Record)
│   │   ├── Field.java           # Field model (Record)
│   │   ├── Type.java            # Type definitions
│   │   └── ParseResult.java     # Result container (Record)
│   └── generator/
│       └── JsonGenerator.java   # JSON output
└── test/
    └── java/
        └── parser/
            ├── StructParserServiceTest.java
            └── MultiFileParserTest.java
```

## Tech Stack

- **Java 26**: Modern Java with Record classes and pattern matching
- **ANTLR 4.13.1**: Parser generator for robust grammar parsing
- **Maven 3.9+**: Build and dependency management
- **JUnit 5**: Unit testing (38 tests)

## Development

### Run Tests

```bash
mvn test
```

### Generate Parser

```bash
mvn antlr4:antlr4
```

### Build Executable JAR

```bash
mvn clean package
# Output: target/struct-parser-1.0.0-jar-with-dependencies.jar
```

## Limitations

- **Forward References**: Not supported (struct must be defined before use)
- **Arrays**: Array syntax (`uint8 arr[4]`) not supported, use expanded form
- **Preprocessing**: Limited to `#include`, no macro expansion
- **Standard C Types**: Only `uint1~uint32` supported, no `int/char/float`

## Roadmap

### v1.2 (Planned)
- [ ] Array type support (`uint8 data[4]`)
- [ ] Forward reference support
- [ ] Enhanced typedef semantics
- [ ] Code generation (C/Python/Rust)

### v2.0 (Planned)
- [ ] Conditional compilation (`#ifdef`)
- [ ] Macro definition support
- [ ] IDE plugin
- [ ] LSP protocol support

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Acknowledgments

- [ANTLR4](https://www.antlr.org/) - Powerful parser generator
- [ANTLR4 Grammars](https://github.com/antlr/grammars-v4) - Community grammar collection
