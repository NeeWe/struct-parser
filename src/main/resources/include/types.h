// 基础类型定义
#ifndef TYPES_H
#define TYPES_H

// 布尔类型
struct BoolField {
    uint1 value;
    uint7 reserved;
};

// 8位寄存器
struct Reg8 {
    uint8 value;
};

// 16位寄存器
struct Reg16 {
    uint16 value;
};

// 32位寄存器
struct Reg32 {
    uint32 value;
};

#endif // TYPES_H
