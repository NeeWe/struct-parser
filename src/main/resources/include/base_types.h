// 基础类型定义
#ifndef BASE_TYPES_H
#define BASE_TYPES_H

// 基础状态结构体
struct Status {
    uint8 code;
    uint8 flags;
};

// 基础配置联合体
union ConfigValue {
    uint32 raw;
    struct {
        uint16 low;
        uint16 high;
    } parts;
};

// 版本信息结构体
struct Version {
    uint8 major;
    uint8 minor;
    uint16 patch;
};

#endif // BASE_TYPES_H
