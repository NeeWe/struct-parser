// 设备类型定义 - 引用基础类型
#ifndef DEVICE_TYPES_H
#define DEVICE_TYPES_H

#include "base_types.h"

// 设备信息结构体 - 引用 Version 和 Status
struct DeviceInfo {
    Version version;
    Status status;
    uint32 device_id;
};

// 设备配置结构体 - 引用 ConfigValue
struct DeviceConfig {
    uint8 mode;
    ConfigValue config;
    uint16 timeout;
};

// 数据包结构体 - 嵌套引用多个基础类型
struct DataPacket {
    Version header_version;
    uint16 length;
    union {
        uint32 checksum;
        struct {
            uint16 crc_low;
            uint16 crc_high;
        } crc_parts;
    } integrity;
    Status packet_status;
};

struct Meta {
    union {
        uint1 flag1;
        uint1 flag2;
    };

    union {
        uint1 flag3;
        uint1 flag4;
    };

    uint22 typeid;
};

#endif // DEVICE_TYPES_H
