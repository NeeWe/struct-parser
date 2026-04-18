package com.structparser;

import com.structparser.config.ConfigLoader;
import com.structparser.config.ParserConfig;
import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import com.structparser.parser.GccPreprocessor;
import com.structparser.parser.StructParserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 应用程序入口 - 强制从配置文件启动，无参数，无默认配置生成
 */
public class StructParserApp {
    
    public static void main(String[] args) {
        // 无参数：执行解析
        if (args.length == 0) {
            parseWithConfig();
            return;
        }
        
        // 有参数：仅支持 help 和 gcc-info
        String command = args[0];
        
        switch (command) {
            case "help":
            case "--help":
            case "-h":
                printUsage();
                break;
                
            case "gcc-info":
                printGccInfo();
                break;
                
            default:
                System.err.println("Error: This program does not accept arguments.");
                System.err.println("Run without arguments to parse using struct-parser.yaml");
                System.err.println("Or use 'help' for usage information.");
                System.exit(1);
        }
    }
    
    /**
     * 强制从配置文件解析头文件
     */
    private static void parseWithConfig() {
        // 检查 GCC 是否可用（强制要求）
        if (!GccPreprocessor.isGccAvailable()) {
            System.err.println("Error: GCC is required but not available.");
            System.err.println("Please install GCC to use this tool.");
            System.exit(1);
        }
        
        // 加载配置文件
        ParserConfig config;
        try {
            config = ConfigLoader.autoLoad(Paths.get("."));
        } catch (IOException e) {
            System.err.println("Error: Configuration file not found.");
            System.err.println();
            System.err.println("Expected one of the following files in current directory:");
            System.err.println("  - struct-parser.yaml");
            System.err.println("  - struct-parser.yml");
            System.err.println("  - struct-parser.json");
            System.err.println();
            System.err.println("Please create a configuration file with the following content:");
            System.err.println();
            System.err.println("headerFile: path/to/your/header.h");
            System.err.println("includePaths:");
            System.err.println("  - ./include");
            System.err.println("gccCommand: gcc");
            System.err.println("gccRequired: true");
            System.exit(1);
            return;
        }
        
        // 验证配置
        try {
            config.validate();
        } catch (IllegalStateException e) {
            System.err.println("Error: Invalid configuration - " + e.getMessage());
            System.exit(1);
            return;
        }
        
        // 解析头文件
        parseWithGccPreprocessing(config);
    }
    
    /**
     * 使用 GCC 预处理解析头文件
     */
    private static void parseWithGccPreprocessing(ParserConfig config) {
        var service = new StructParserService();
        var generator = new JsonGenerator();
        
        // 配置 GCC 预处理（强制启用）
        service.enableGccPreprocessing();
        service.setGccCommand(config.gccCommand());
        
        // 添加包含路径
        for (Path path : config.getIncludePaths()) {
            service.addSearchPath(path);
        }
        
        try {
            Path headerPath = config.getHeaderFilePath();
            System.err.println("Parsing: " + headerPath);
            System.err.println("Using GCC: " + config.gccCommand());
            
            ParseResult result = service.parseFile(headerPath);
            String json = generator.generate(result);
            
            // 输出结果
            if (config.output() != null && config.output().outputFile() != null) {
                // 写入文件
                Path outputPath = Paths.get(config.output().outputFile());
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, json);
                System.err.println("Output written to: " + outputPath);
            } else {
                // 输出到标准输出
                System.out.println(json);
            }
            
            // 报告错误
            if (result.hasErrors()) {
                System.err.println("\nParsing completed with errors:");
                for (String error : result.errors()) {
                    System.err.println("  - " + error);
                }
                System.exit(2);
            } else {
                System.err.println("Parsing completed successfully.");
            }
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void printGccInfo() {
        System.out.println("GCC Preprocessor Information");
        System.out.println("============================");
        System.out.println();
        
        boolean available = GccPreprocessor.isGccAvailable();
        System.out.println("GCC Available: " + (available ? "Yes ✓" : "No ✗"));
        
        if (available) {
            System.out.println("GCC Version: " + GccPreprocessor.getGccVersion());
            System.out.println();
            System.out.println("Status: Ready to parse header files with GCC preprocessing.");
        } else {
            System.out.println();
            System.out.println("Status: GCC is required but not found in PATH.");
            System.out.println("Please install GCC to use this tool.");
            System.out.println();
            System.out.println("Installation:");
            System.out.println("  macOS:  xcode-select --install");
            System.out.println("  Ubuntu: sudo apt-get install gcc");
            System.out.println("  Windows: Install MinGW-w64 or WSL");
        }
    }
    
    private static void printUsage() {
        System.out.println("Struct Parser - C-style struct/union parser with GCC preprocessing");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar struct-parser.jar           Parse using struct-parser.yaml");
        System.out.println("  java -jar struct-parser.jar gcc-info  Check GCC availability");
        System.out.println("  java -jar struct-parser.jar help      Show this help message");
        System.out.println();
        System.out.println("Configuration File:");
        System.out.println("  Required: struct-parser.yaml (or .yml, .json) in current directory");
        System.out.println();
        System.out.println("  Example struct-parser.yaml:");
        System.out.println("    headerFile: src/registers.h");
        System.out.println("    includePaths:");
        System.out.println("      - ./include");
        System.out.println("      - ./drivers");
        System.out.println("    gccCommand: gcc");
        System.out.println("    gccRequired: true");
        System.out.println("    output:");
        System.out.println("      format: json");
        System.out.println("      outputFile: output.json");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  - GCC must be installed and in PATH");
        System.out.println("  - Configuration file must exist");
        System.out.println("  - Header file path must be specified in configuration");
    }
}
