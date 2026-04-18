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
 * 应用程序入口 - 强制从配置文件启动
 */
public class StructParserApp {
    
    public static void main(String[] args) {
        // 无参数或帮助命令
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h")) {
            printUsage();
            System.exit(0);
        }
        
        String command = args[0];
        
        switch (command) {
            case "parse":
                // 强制从配置文件启动，不接受其他参数
                if (args.length > 1) {
                    System.err.println("Error: This command does not accept additional arguments.");
                    System.err.println("Configuration must be specified in struct-parser.yaml");
                    printUsage();
                    System.exit(1);
                }
                parseWithConfig();
                break;
                
            case "init":
                // 可选：指定输出路径
                Path outputPath = args.length > 1 ? Paths.get(args[1]) : Paths.get("struct-parser.yaml");
                generateDefaultConfig(outputPath);
                break;
                
            case "gcc-info":
                printGccInfo();
                break;
                
            default:
                System.err.println("Unknown command: " + command);
                printUsage();
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
            // 自动查找配置文件
            try {
                config = ConfigLoader.autoLoad(Paths.get("."));
            } catch (IOException e) {
                // 未找到配置文件，生成默认配置
                Path defaultConfigPath = Paths.get("struct-parser.yaml");
                System.err.println("Configuration file not found.");
                System.err.println();
                generateDefaultConfig(defaultConfigPath);
                System.err.println();
                System.err.println("Please edit the headerFile path in " + defaultConfigPath + " and run again.");
                System.exit(1);
                return;
            }
        } catch (Exception e) {
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
    
    /**
     * 生成默认配置文件
     */
    private static void generateDefaultConfig(Path outputPath) {
        var defaultConfig = new ParserConfig(
            "src/registers.h",
            List.of("./include", "./drivers", "./hal"),
            "gcc",
            true,
            new ParserConfig.OutputConfig("json", null)
        );
        
        try {
            ConfigLoader.save(defaultConfig, outputPath);
            System.out.println("Default configuration generated: " + outputPath);
            System.out.println();
            System.out.println("Configuration contents:");
            System.out.println("  headerFile: src/registers.h");
            System.out.println("  includePaths: [./include, ./drivers, ./hal]");
            System.out.println("  gccCommand: gcc");
            System.out.println("  gccRequired: true");
            System.out.println();
            System.out.println("Please edit the headerFile path to point to your header file.");
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
        System.out.println("  java -jar struct-parser.jar <command>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  parse              Parse header file specified in struct-parser.yaml");
        System.out.println("                     Configuration file is required, no arguments accepted");
        System.out.println("  init [file]        Generate default configuration file");
        System.out.println("                     Default: struct-parser.yaml");
        System.out.println("  gcc-info           Check GCC availability and version");
        System.out.println("  help               Show this help message");
        System.out.println();
        System.out.println("Configuration File (struct-parser.yaml):");
        System.out.println("  The configuration file is required and must be in the current directory.");
        System.out.println("  It specifies the header file path and parsing options.");
        System.out.println();
        System.out.println("  Required fields:");
        System.out.println("    headerFile: Path to the header file to parse");
        System.out.println();
        System.out.println("  Optional fields:");
        System.out.println("    includePaths: List of include search paths");
        System.out.println("    gccCommand:   GCC command to use (default: gcc)");
        System.out.println("    output.format:     Output format (json)");
        System.out.println("    output.outputFile: Output file path (default: stdout)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Generate default configuration");
        System.out.println("  java -jar struct-parser.jar init");
        System.out.println();
        System.out.println("  # Edit struct-parser.yaml, then parse");
        System.out.println("  java -jar struct-parser.jar parse");
        System.out.println();
        System.out.println("  # Check GCC availability");
        System.out.println("  java -jar struct-parser.jar gcc-info");
    }
}
