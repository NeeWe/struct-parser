// Preprocessed output - all #include, #define, #ifdef etc. have been removed by GCC
// This file contains mixed C syntax that should be ignored by the parser

// Function declarations (should be ignored)
void init_device(void);
int process_data(unsigned int data);

// Enum definition (should be ignored)
enum Color {
    RED = 0,
    GREEN = 1,
    BLUE = 2
};

// This struct should be parsed
struct ControlReg {
    uint1 enable;
    uint1 interrupt;
    uint2 mode;
    uint4 reserved;
    uint8 prescale;
    uint16 timeout;
};

// More C code to ignore
static inline void helper_function(void) {
    // do something
}

const int SOME_CONSTANT = 42;

// Another struct
struct Status {
    uint8 code;
    uint8 flags;
};

// Union definition
union DataValue {
    uint32 raw;
    struct {
        uint16 low;
        uint16 high;
    } words;
};

// More functions and variables
extern void external_func(int param);
static const char* get_version(void) {
    return "1.0.0";
}
