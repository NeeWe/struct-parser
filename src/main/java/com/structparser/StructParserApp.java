package com.structparser;

import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import com.structparser.parser.GccPreprocessor;
import com.structparser.parser.StructParserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用程序入口 - 支持 GCC 预处理和自定义 #include
 */
public class StructParserApp {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String command = args[0];
        
        switch (command) {
            case "parse":
                if (args.length < 2) {
                    System.err.println("Error: Missing input file path");
                    printUsage();
                    System.exit(1);
                }
                parseFile(args);
                break;
                
            case "example":
                parseExample();
                break;
                
            case "gcc-info":
                printGccInfo();
                break;
                
            case "help":
            default:
                printUsage();
                break;
        }
    }
    
    private static void parseFile(String[] args) {
        String filePath = args[1];
        boolean useGcc = false;
        String gccCommand = "gcc";
        var searchPaths = new java.util.ArrayList<String>();
        
        // 解析选项
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "-I":
                    if (i + 1 < args.length) {
                        searchPaths.add(args[i + 1]);
                        i++;
                    }
                    break;
                case "--gcc":
                    useGcc = true;
                    break;
                case "--gcc-cmd":
                    if (i + 1 < args.length) {
                        gccCommand = args[i + 1];
                        i++;
                    }
                    break;
            }
        }
        
        var service = new StructParserService();
        var generator = new JsonGenerator();
        
        // 添加搜索路径
        for (String path : searchPaths) {
            service.addSearchPath(path);
        }
        
        // 配置 GCC 预处理
        if (useGcc) {
            if (!StructParserService.isGccAvailable()) {
                System.err.println("Error: GCC is not available. Please install GCC or remove --gcc option.");
                System.exit(1);
            }
            service.enableGccPreprocessing();
            service.setGccCommand(gccCommand);
        }
        
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                System.err.println("Error: File not found: " + filePath);
                System.exit(1);
            }
            
            ParseResult result = service.parseFile(path);
            String json = generator.generate(result);
            
            System.out.println(json);
            
            if (result.hasErrors()) {
                System.err.println("\nParsing completed with errors:");
                for (String error : result.errors()) {
                    System.err.println("  - " + error);
                }
                System.exit(2);
            }
            
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void parseExample() {
        String example = """
            // 控制寄存器定义
            struct ControlReg {
                uint1  enable;
                uint1  interrupt;
                uint2  mode;
                uint4  reserved;
                uint8  prescale;
                uint16 timeout;
            };
            
            // 数据包头部
            struct PacketHeader {
                uint8  version;
                uint8  type;
                uint16 length;
                union {
                    uint32 raw;
                    struct {
                        uint16 low;
                        uint16 high;
                    } words;
                } checksum;
            };
            """;
        
        var service = new StructParserService();
        var generator = new JsonGenerator();
        
        ParseResult result = service.parse(example);
        String json = generator.generate(result);
        
        System.out.println("=== Example Input ===");
        System.out.println(example);
        System.out.println("\n=== Parsed Output ===");
        System.out.println(json);
        
        if (result.hasErrors()) {
            System.err.println("\nParsing completed with errors.");
        }
    }
    
    private static void printGccInfo() {
        System.out.println("GCC Preprocessor Information");
        System.out.println("============================");
        System.out.println();
        
        boolean available = GccPreprocessor.isGccAvailable();
        System.out.println("GCC Available: " + (available ? "Yes" : "No"));
        
        if (available) {
            System.out.println("GCC Version: " + GccPreprocessor.getGccVersion());
        } else {
            System.out.println("GCC is not found in PATH.");
            System.out.println("Please install GCC to use --gcc option.");
        }
    }
    
    private static void printUsage() {
        System.out.println("Struct Parser - C-style struct/union parser with #include support");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar struct-parser.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  parse <file> [options]     Parse a struct definition file");
        System.out.println("    Options:");
        System.out.println("      -I <path>              Add include search path");
        System.out.println("      --gcc                  Use GCC preprocessor (gcc -E)");
        System.out.println("      --gcc-cmd <command>    Use custom GCC command (e.g., arm-none-eabi-gcc)");
        System.out.println("  example                    Run with built-in example");
        System.out.println("  gcc-info                   Check GCC availability");
        System.out.println("  help                       Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Parse with custom #include handler");
        System.out.println("  java -jar struct-parser.jar parse input.h -I ./include");
        System.out.println();
        System.out.println("  # Parse with GCC preprocessing");
        System.out.println("  java -jar struct-parser.jar parse input.h --gcc -I ./include");
        System.out.println();
        System.out.println("  # Use cross-compiler for preprocessing");
        System.out.println("  java -jar struct-parser.jar parse input.h --gcc --gcc-cmd arm-none-eabi-gcc");
    }
}
