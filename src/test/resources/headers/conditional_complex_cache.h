// Preprocessed output with HIGH_PERF and ENABLE_CACHE defined
// Original: conditional_complex.h with -DHIGH_PERF -DENABLE_CACHE

struct CacheConfig {
    uint16 size;
    uint8 associativity;
    uint8 policy;
};

struct GlobalSettings {
    uint8 version;
    uint8 checksum;
};
