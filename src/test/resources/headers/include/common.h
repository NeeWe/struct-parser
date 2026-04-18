// 公共头文件
#ifndef COMMON_H
#define COMMON_H

#include "../types.h"

// 状态码
union Status {
    uint8 raw;
    struct {
        uint1 busy;
        uint1 error;
        uint1 ready;
        uint5 reserved;
    } flags;
};

// 版本信息
struct Version {
    uint8 major;
    uint8 minor;
    uint16 patch;
};

#endif // COMMON_H
