// Test case: Complex conditional compilation with multiple conditions
// This file tests complex #if expressions with &&, ||, defined()

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

// Nested conditions
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

// Always present regardless of conditions
struct GlobalSettings {
    uint8 version;
    uint8 checksum;
};
