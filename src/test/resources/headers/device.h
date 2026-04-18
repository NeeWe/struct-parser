// 设备寄存器定义
#ifndef DEVICE_H
#define DEVICE_H

#include "include/common.h"

// 控制寄存器
struct ControlReg {
    uint1 enable;
    uint1 interrupt;
    uint2 mode;
    uint4 reserved;
    uint8 prescale;
    uint16 timeout;
};

// 状态寄存器
struct StatusReg {
    union Status status;
    uint8 error_code;
    uint16 error_addr;
};

// 数据寄存器
struct DataReg {
    struct Reg32 data;
    uint8 valid;
    uint8 reserved0;
    uint8 reserved1;
    uint8 reserved2;
};

// 设备配置
struct DeviceConfig {
    struct Version version;
    struct ControlReg control;
    struct StatusReg status;
    struct DataReg data;
};

#endif // DEVICE_H
