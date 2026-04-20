// Test case: Conditional compilation with macros defined in external header
// The macros are NOT defined in this file, but included via -include flag

#ifdef FEATURE_A
struct FeatureAConfig {
    uint8 mode;
    uint8 reserved;
    uint16 timeout;
};
#else
struct FeatureBConfig {
    uint8 level;
    uint24 value;
};
#endif

#if defined(HIGH_PERF) && defined(ENABLE_CACHE)
struct CacheConfig {
    uint16 size;
    uint8 associativity;
    uint8 policy;
};
#elif defined(HIGH_PERF) || defined(MEDIUM_PERF)
struct PerformanceConfig {
    uint8 priority;
    uint24 threshold;
};
#else
struct BasicConfig {
    uint8 mode;
    uint8 reserved;
};
#endif

#ifdef FEATURE_X
    #ifdef DEBUG_MODE
        struct DebugFeatureX {
            uint32 trace_buffer;
            uint16 log_level;
        };
    #else
        struct ReleaseFeatureX {
            uint16 config;
        };
    #endif
#endif

struct CommonSettings {
    uint8 version;
    uint8 checksum;
};
