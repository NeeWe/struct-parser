# Struct Parser

[![Java 26](https://img.shields.io/badge/Java-26-blue.svg)](https://openjdk.org/projects/jdk/26/)
[![ANTLR4](https://img.shields.io/badge/ANTLR-4.13.1-orange.svg)](https://www.antlr.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-green.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A C-style struct/union parser with ANTLR4 and GCC preprocessing, designed for embedded systems and hardware register description. Supports configuration-driven parsing, cross-file struct references, and generates JSON output with bit-level field layout.

## Features

- **Struct/Union Parsing**: Parse C-style struct and union definitions
- **Nested Types**: Support nested and anonymous struct/union
- **Custom Types**: uint1~uint32 data types with implicit bit-width
- **No Byte Alignment**: Fields are packed tightly (bit-level layout)
- **GCC Preprocessing**: Full C preprocessor support via `gcc -E -P` (comments removed)
- **Circular Reference Detection**: Detect and reject self-references and cross-references
- **Configuration-Driven**: YAML/JSON configuration for batch parsing
- **Cross-File References**: Reference structs defined in other headers
- **JSON Output**: Generate structured JSON with field offsets and sizes

## Quick Start

### Prerequisites

- Java 26 or later
- GCC installed (required for preprocessing)

### Build

```bash
mvn clean package
```

### Configuration

Create `struct-parser.yaml` in your working directory:

```yaml
compileConfigFile: ./command.txt
output:
  format: json
  outputFile: output.json
```

The `compileConfigFile` should contain a simple gcc command:

```txt
gcc -E -P -I./include -I./drivers
```

### Run

```bash
# Parse all headers in configured includePaths
java -jar target/struct-parser-1.0.0-jar-with-dependencies.jar

# Check GCC availability
java -jar target/struct-parser-1.0.0-jar-with-dependencies.jar gcc-info

# Show help
java -jar target/struct-parser-1.0.0-jar-with-dependencies.jar help
```

## Example

### Input (Header Files)

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

## Configuration

### Configuration File

The tool requires a configuration file (`struct-parser.yaml`, `struct-parser.yml`, or `struct-parser.json`) in the working directory.

### Configuration Options

| Option | Required | Default | Description |
|--------|----------|---------|-------------|
| `compileConfigFile` | Yes | - | Path to compile config file (contains gcc command) |
| `output.format` | No | `json` | Output format (currently only `json`) |
| `output.outputFile` | No | stdout | Output file path (if not specified, prints to stdout) |

### Compile Config File Format

The compile config file is a simple text file containing a gcc preprocessing command:

```txt
gcc -E -P -I./include -I./drivers -I./hal
```

**Note**: Only direct command format is supported (no JSON Compilation Database or Makefile).

### Example Configurations

**Basic YAML:**
```yaml
compileConfigFile: ./command.txt
```

**With output file:**
```yaml
compileConfigFile: ./build/command.txt
output:
  format: json
  outputFile: output/structs.json
```

**JSON format:**
```json
{
  "compileConfigFile": "./command.txt",
  "output": {
    "format": "json",
    "outputFile": "output.json"
  }
}
```

**Example command.txt:**
```txt
gcc -E -P -I./include -nostdinc
```

## Multi-File Support

Reference structs from other headers:

```c
// base_types.h
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

// device_types.h
#include "base_types.h"

struct DeviceInfo {
    Status status;      // Reference struct from base_types.h
    uint32 device_id;
};

struct DeviceConfig {
    uint8 mode;
    ConfigValue config; // Reference union from base_types.h
    uint16 timeout;
};
```

Place both files in directories listed in `includePaths`, and the parser will resolve cross-file references after GCC preprocessing.

**Note**: Forward references and circular references are not allowed. Referenced types must be defined before use.

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

## How It Works

1. **Configuration Loading**: Reads `struct-parser.yaml` (or `.yml`, `.json`) and loads compile config
2. **Header File Scanning**: Scans directories from compile config for header files
3. **GCC Preprocessing**: Runs gcc command from compile config to preprocess headers (comments removed)
4. **Two-Pass Parsing**:
   - First pass: Collect all top-level struct/union names
   - Second pass: Parse fields and detect circular references
5. **Circular Reference Detection**: Check for self-references, bidirectional, and multi-way cycles
6. **Result Merging**: Merges results from all header files
7. **JSON Generation**: Outputs structured JSON with field offsets and sizes

## Architecture

```
src/
├── main/antlr4/
│   └── StructParser.g4              # ANTLR4 grammar
├── main/java/
│   ├── config/
│   │   ├── ParserConfig.java        # Configuration model (Record)
│   │   └── ConfigLoader.java        # YAML/JSON config loader
│   ├── parser/
│   │   ├── StructParserService.java # Main parsing service
│   │   ├── StructParseVisitor.java  # AST visitor (two-pass scanning)
│   │   ├── GccPreprocessor.java     # GCC preprocessing (direct command only)
│   │   └── HeaderFileScanner.java   # Header file discovery
│   ├── model/
│   │   ├── Struct.java              # Struct model (Record)
│   │   ├── Union.java               # Union model (Record)
│   │   ├── Field.java               # Field model (Record)
│   │   ├── Type.java                # Type definitions
│   │   └── ParseResult.java         # Result container (Record)
│   └── generator/
│       └── JsonGenerator.java       # JSON output generator
└── test/
    └── java/
        └── parser/
            ├── StructParserServiceTest.java
            ├── GccPreprocessorTest.java
            ├── MultiFileParserTest.java
            ├── MultiFileReferenceTest.java    # Cross-file reference tests
            ├── CircularReferenceTest.java     # Circular reference detection
            └── integration/
                └── GccIntegrationTest.java
```

## Tech Stack

- **Java 26**: Modern Java with Record classes, Switch Expressions, and Pattern Matching
- **ANTLR 4.13.1**: Parser generator for robust grammar parsing
- **Maven 3.9+**: Build and dependency management
- **JUnit 5**: Unit testing (112+ tests)
- **Jackson**: JSON and YAML processing
- **GCC**: C preprocessor for header files (`gcc -E -P`)

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
- **Circular References**: Not allowed (self-reference, bidirectional, multi-way cycles)
- **Arrays**: Array syntax (`uint8 arr[4]`) not supported, use expanded form
- **Standard C Types**: Only `uint1~uint32` supported, no `int/char/float`
- **Comments**: Removed during GCC preprocessing (`gcc -E -P`)

## Roadmap

### v1.3 (Planned)
- [ ] Array type support (`uint8 data[4]`)
- [ ] Enhanced typedef semantics
- [ ] Code generation (C/Python/Rust)
- [ ] Bit-field alignment options

### v2.0 (Planned)
- [ ] Conditional compilation (`#ifdef`)
- [ ] Macro definition support
- [ ] IDE plugin
- [ ] LSP protocol support

## Documentation

- [Requirements](doc/requirements.md) - Detailed requirements document
- [Wiki](doc/WIKI.md) - Project wiki with usage guide

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Acknowledgments

- [ANTLR4](https://www.antlr.org/) - Powerful parser generator
- [ANTLR4 Grammars](https://github.com/antlr/grammars-v4) - Community grammar collection
