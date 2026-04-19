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

#endif // TYPES_H
