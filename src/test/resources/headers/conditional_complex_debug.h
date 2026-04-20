// Preprocessed output with FEATURE_X and DEBUG_MODE defined
// Original: conditional_complex.h with -DFEATURE_X -DDEBUG_MODE

struct BasicConfig {
    uint8 mode;
    uint8 reserved;
};

struct DebugFeatureX {
    uint32 trace_buffer;
    uint16 log_level;
};

struct GlobalSettings {
    uint8 version;
    uint8 checksum;
};
