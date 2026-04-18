// 循环引用测试 A
#ifndef CIRCULAR_A_H
#define CIRCULAR_A_H

#include "circular_b.h"

struct StructA {
    uint8 field_a;
    struct StructB ref_b;
};

#endif // CIRCULAR_A_H
