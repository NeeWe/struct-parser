// Test case: Simple conditional compilation
// After preprocessing with FEATURE_A defined, only FeatureAConfig should exist
// After preprocessing without FEATURE_A, only FeatureBConfig should exist

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

// This struct always exists
struct CommonConfig {
    uint8 id;
    uint8 flags;
};
