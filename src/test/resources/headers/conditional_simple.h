// Test case: Simple conditional compilation with #ifdef
// This file contains preprocessor directives that will be handled by GCC

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

// This struct always exists regardless of conditions
struct CommonConfig {
    uint8 id;
    uint8 flags;
};
