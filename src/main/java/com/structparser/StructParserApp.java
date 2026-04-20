package com.structparser;

import com.structparser.config.ConfigLoader;
import com.structparser.config.ParserConfig;
import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import com.structparser.model.Struct;
import com.structparser.model.Union;
import com.structparser.parser.GccPreprocessor;
import com.structparser.parser.HeaderFileScanner;
import com.structparser.parser.StructParserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用程序入口 - 扫描并解析 includePaths 中的所有头文件
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
     * 从配置文件扫描并解析所有头文件
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
            System.err.println("compileConfigFile: compile_commands.json  # or Makefile, or command.txt");
            System.err.println("output:");
            System.err.println("  format: json");
            System.err.println("  outputFile: output/structs.json");
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
        
        // 从编译配置文件提取头文件路径
        Path compileConfigPath = Paths.get(config.compileConfigFile());
        List<Path> headerFiles;
        try {
            headerFiles = extractHeaderFilesFromCompileConfig(compileConfigPath);
        } catch (IOException e) {
            System.err.println("Error extracting header files from compile config: " + e.getMessage());
            System.exit(1);
            return;
        }
        
        if (headerFiles.isEmpty()) {
            System.err.println("Error: No header files found in compile config: " + compileConfigPath);
            System.exit(1);
            return;
        }
        
        System.err.println("Found " + headerFiles.size() + " header file(s) to parse:");
        for (Path file : headerFiles) {
            System.err.println("  - " + file);
        }
        System.err.println();
        
        // 解析所有头文件
        parseAllHeaders(config, headerFiles);
    }
    
    /**
     * 从编译配置文件提取头文件列表
     */
    private static List<Path> extractHeaderFilesFromCompileConfig(Path compileConfigPath) throws IOException {
        // 简化实现：扫描编译配置文件所在目录及其子目录中的头文件
        // TODO: 实际应该解析 compile_commands.json 或 Makefile 来提取具体的头文件
        Path baseDir = compileConfigPath.getParent();
        if (baseDir == null) {
            baseDir = Paths.get(".");
        }
        return HeaderFileScanner.scan(List.of(baseDir));
    }
    
    /**
     * 解析所有头文件并合并结果
     */
    private static void parseAllHeaders(ParserConfig config, List<Path> headerFiles) {
        var service = new StructParserService();
        var generator = new JsonGenerator();
        
        // 加载编译配置
        try {
            Path compileConfigPath = Paths.get(config.compileConfigFile());
            service.loadCompileConfig(compileConfigPath);
        } catch (IOException e) {
            System.err.println("Error loading compile config: " + e.getMessage());
            System.exit(1);
            return;
        }
        
        // 合并所有解析结果
        var allStructs = new ArrayList<Struct>();
        var allUnions = new ArrayList<Union>();
        var allErrors = new ArrayList<String>();
        
        for (Path headerFile : headerFiles) {
            System.err.println("Parsing: " + headerFile);
            
            try {
                ParseResult result = service.parseFile(headerFile);
                
                allStructs.addAll(result.structs());
                allUnions.addAll(result.unions());
                allErrors.addAll(result.errors());
                
            } catch (IOException e) {
                allErrors.add("Error parsing " + headerFile + ": " + e.getMessage());
            }
        }
        
        // 创建合并的解析结果
        var mergedResult = new ParseResult(allStructs, allUnions, java.util.Map.of(), allErrors);
        
        // 生成输出
        String json = generator.generate(mergedResult);
        
        // 输出结果
        try {
            if (config.output() != null && config.output().outputFile() != null) {
                // 写入文件
                Path outputPath = Paths.get(config.output().outputFile());
                Files.createDirectories(outputPath.getParent());
                Files.writeString(outputPath, json);
                System.err.println("\nOutput written to: " + outputPath);
            } else {
                // 输出到标准输出
                System.out.println(json);
            }
        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
            System.exit(1);
        }
        
        // 报告结果
        System.err.println("\nParsing completed:");
        System.err.println("  - Structs: " + allStructs.size());
        System.err.println("  - Unions: " + allUnions.size());
        
        if (!allErrors.isEmpty()) {
            System.err.println("  - Errors: " + allErrors.size());
            for (String error : allErrors) {
                System.err.println("    - " + error);
            }
            System.exit(2);
        } else {
            System.err.println("  - Status: Success");
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
        System.out.println("  java -jar struct-parser.jar           Parse all headers from compile config");
        System.out.println("  java -jar struct-parser.jar gcc-info  Check GCC availability");
        System.out.println("  java -jar struct-parser.jar help      Show this help message");
        System.out.println();
        System.out.println("Configuration File (struct-parser.yaml):");
        System.out.println("  Required in current directory");
        System.out.println();
        System.out.println("  Example:");
        System.out.println("    compileConfigFile: compile_commands.json  # or Makefile, or command.txt");
        System.out.println("    output:");
        System.out.println("      format: json");
        System.out.println("      outputFile: output.json");
        System.out.println();
        System.out.println("Compile Config File Formats:");
        System.out.println("  1. compile_commands.json - JSON Compilation Database");
        System.out.println("  2. Makefile - Will extract CFLAGS/CPPFLAGS");
        System.out.println("  3. command.txt - Direct gcc command with flags");
        System.out.println();
        System.out.println("Features:");
        System.out.println("  - Extracts header files from compile config directory");
        System.out.println("  - GCC preprocessing is mandatory");
        System.out.println("  - Merges results from all header files");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  - GCC must be installed and in PATH");
        System.out.println("  - Configuration file must exist");
        System.out.println("  - Compile config file must exist");
    }
}
