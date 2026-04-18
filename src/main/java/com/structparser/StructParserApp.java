package com.structparser;

import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import com.structparser.parser.StructParserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用程序入口 - 支持文件解析和 #include
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
                parseFile(args[1], extractSearchPaths(args));
                break;
                
            case "example":
                parseExample();
                break;
                
            case "help":
            default:
                printUsage();
                break;
        }
    }
    
    /**
     * 从命令行参数中提取搜索路径（-I 选项）
     */
    private static String[] extractSearchPaths(String[] args) {
        var paths = new java.util.ArrayList<String>();
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("-I") && i + 1 < args.length) {
                paths.add(args[i + 1]);
                i++;
            }
        }
        return paths.toArray(new String[0]);
    }
    
    private static void parseFile(String filePath, String[] searchPaths) {
        var service = new StructParserService();
        var generator = new JsonGenerator();
        
        // 添加搜索路径
        for (String path : searchPaths) {
            service.addSearchPath(path);
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
                System.err.println("\nParsing completed with errors.");
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
    
    private static void printUsage() {
        System.out.println("Struct Parser - C-style struct/union parser with #include support");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar struct-parser.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  parse <file> [-I <path>]   Parse a struct definition file");
        System.out.println("                             -I: Add include search path");
        System.out.println("  example                    Run with built-in example");
        System.out.println("  help                       Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar struct-parser.jar parse input.h");
        System.out.println("  java -jar struct-parser.jar parse input.h -I ./include -I /usr/local/include");
        System.out.println("  java -jar struct-parser.jar example");
    }
}
