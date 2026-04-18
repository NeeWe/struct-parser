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

/**
 * 应用程序入口 - 强制使用配置文件和 GCC 预处理
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
                parseWithConfig(args);
                break;
                
            case "init":
                generateExampleConfig(args);
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
    
    /**
     * 使用配置文件解析头文件
     */
    private static void parseWithConfig(String[] args) {
        // 检查 GCC 是否可用（强制要求）
        if (!GccPreprocessor.isGccAvailable()) {
            System.err.println("Error: GCC is required but not available.");
            System.err.println("Please install GCC to use this tool.");
            System.exit(1);
        }
        
        // 加载配置文件
        ParserConfig config;
        Path configPath = null;
        try {
            if (args.length >= 2) {
                // 使用指定的配置文件
                configPath = Paths.get(args[1]);
                config = ConfigLoader.load(configPath);
            } else {
                // 自动查找配置文件
                try {
                    config = ConfigLoader.autoLoad(Paths.get("."));
                } catch (IOException e) {
                    // 未找到配置文件，生成默认配置
                    System.err.println("Configuration file not found. Generating default config...");
                    configPath = Paths.get("struct-parser.yaml");
                    generateDefaultConfig(configPath);
                    System.err.println("Default configuration generated: " + configPath);
                    System.err.println("Please edit the headerFile path and run again.");
                    System.exit(1);
                    return;
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
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
    
    /**
     * 生成示例配置文件
     */
    private static void generateExampleConfig(String[] args) {
        Path outputPath = args.length >= 2 ? Paths.get(args[1]) : Paths.get("struct-parser.yaml");
        generateDefaultConfig(outputPath);
    }
    
    /**
     * 生成默认配置文件
     */
    private static void generateDefaultConfig(Path outputPath) {
        var defaultConfig = new ParserConfig(
            "src/registers.h",
            java.util.List.of("./include", "./drivers", "./hal"),
            "gcc",
            true,
            new ParserConfig.OutputConfig("json", null)
        );
        
        try {
            ConfigLoader.save(defaultConfig, outputPath);
            System.out.println("Default configuration generated: " + outputPath);
            System.out.println("\nPlease edit the headerFile path and run again.");
        } catch (IOException e) {
            System.err.println("Error generating config: " + e.getMessage());
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
        System.out.println("  java -jar struct-parser.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  parse [config-file]        Parse header file specified in config");
        System.out.println("                             Uses struct-parser.yaml if no config specified");
        System.out.println("  init [output-file]         Generate example configuration file");
        System.out.println("                             Default: struct-parser.yaml");
        System.out.println("  gcc-info                   Check GCC availability and version");
        System.out.println("  help                       Show this help message");
        System.out.println();
        System.out.println("Configuration File (YAML/JSON):");
        System.out.println("  headerFile:    Path to the header file to parse (required)");
        System.out.println("  includePaths:  List of include search paths");
        System.out.println("  gccCommand:    GCC command to use (default: gcc)");
        System.out.println("  output:        Output configuration");
        System.out.println("    format:      Output format (json)");
        System.out.println("    outputFile:  Output file path (optional, stdout if not set)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Generate example config");
        System.out.println("  java -jar struct-parser.jar init");
        System.out.println();
        System.out.println("  # Parse with auto-detected config");
        System.out.println("  java -jar struct-parser.jar parse");
        System.out.println();
        System.out.println("  # Parse with specific config");
        System.out.println("  java -jar struct-parser.jar parse my-config.yaml");
        System.out.println();
        System.out.println("  # Check GCC");
        System.out.println("  java -jar struct-parser.jar gcc-info");
    }
}
