// 基础类型定义
#ifndef TYPES_H
#define TYPES_H

struct A {
  uint7 a;
  uint1 b;
};

struct B {
  uint4 c;
  uint4 d;
};

struct C {
  A ref_a;
  B ref_b;
};

struct D {
  struct {
    uint10 e;
    uint6 f;
  } g;
  union {
    uint16 h;
    uint16 i;
  } j;
};

// 复杂嵌套：union 内包含 struct
union DataValue {
  uint32 raw;
  struct {
    uint16 low;
    uint16 high;
  } words;
};

// 多层嵌套结构体
struct NestedOuter {
  uint8 header;
  struct {
    uint8 x;
    uint8 y;
    struct {
      uint4 red;
      uint4 green;
      uint4 blue;
      uint4 alpha;
    } color;
  } position;
  uint16 footer;
};

// 包含多个 union 的复杂结构体
struct ComplexDevice {
  uint8 device_id;
  union {
    uint16 status_code;
    struct {
      uint1 busy;
      uint1 error;
      uint1 ready;
      uint13 reserved;
    } flags;
  } status;
  union {
    uint32 raw_data;
    struct {
      uint8 byte0;
      uint8 byte1;
      uint8 byte2;
      uint8 byte3;
    } bytes;
    struct {
      uint16 word0;
      uint16 word1;
    } words;
  } data;
  uint8 checksum;
};

// 深层嵌套（3层）
struct DeepNested {
  struct {
    struct {
      struct {
        uint1 flag;
        uint7 reserved;
      } innermost;
      uint8 middle;
    } middle_layer;
    uint16 outer;
  } level1;
};

#endif // TYPES_H
