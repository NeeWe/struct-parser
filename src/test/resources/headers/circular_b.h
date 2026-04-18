// 循环引用测试 B
#ifndef CIRCULAR_B_H
#define CIRCULAR_B_H

#include "circular_a.h"

struct StructB {
    uint16 field_b;
    struct StructA ref_a;
};

#endif // CIRCULAR_B_H
