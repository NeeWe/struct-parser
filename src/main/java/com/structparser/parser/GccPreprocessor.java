package com.structparser.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GCC 预处理器 - 使用 gcc -E 进行标准 C 预处理
 */
public class GccPreprocessor {
    
    private static final Logger logger = LoggerFactory.getLogger(GccPreprocessor.class);
    private static final Logger preprocessLogger = LoggerFactory.getLogger("com.structparser.parser.GccPreprocessor.preprocess");
    
    private List<String> preprocessCommand = null;
    
    /**
     * 从编译配置文件加载预处理命令
     * 仅支持直接命令文件格式（类C DSL的gcc命令）
     */
    public GccPreprocessor loadCompileConfig(Path configFile) throws IOException {
        logger.info("Loading compile config from: {}", configFile.toAbsolutePath());
        String content = Files.readString(configFile);
        
        // 解析直接命令
        preprocessCommand = parseDirectCommand(content);
        
        if (preprocessCommand == null || preprocessCommand.isEmpty()) {
            logger.error("Failed to extract preprocessing command from: {}", configFile);
            throw new IOException("Failed to extract preprocessing command from: " + configFile);
        }
        
        logger.debug("Loaded preprocess command: {}", String.join(" ", preprocessCommand));
        return this;
    }
    
    /**
     * 解析直接命令
     */
    private List<String> parseDirectCommand(String content) {
        return buildPreprocessCommand(content.trim());
    }
    
    /**
     * 构建预处理命令
     */
    private List<String> buildPreprocessCommand(String baseCommand) {
        List<String> command = new ArrayList<>();
        
        // 分割命令（简单实现，不处理引号）
        String[] parts = baseCommand.split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                command.add(part);
            }
        }
        
        // 确保有 gcc 命令
        if (command.isEmpty() || !command.get(0).contains("gcc")) {
            command.add(0, "gcc");
        }
        
        // 添加预处理选项
        if (!command.contains("-E")) {
            int insertPos = 1;
            command.add(insertPos++, "-E");
        }
        if (!command.contains("-P")) {
            command.add("-P");
        }
        
        return command;
    }
    
    /**
     * 预处理文件
     */
    public PreprocessResult preprocess(Path file) throws IOException {
        if (preprocessCommand == null || preprocessCommand.isEmpty()) {
            logger.error("Compile config not loaded. Call loadCompileConfig() first.");
            throw new IllegalStateException("Compile config not loaded. Call loadCompileConfig() first.");
        }
        
        logger.info("Preprocessing file: {}", file.toAbsolutePath());
        
        // 复制命令并添加输入文件
        var command = new ArrayList<>(preprocessCommand);
        
        // 移除可能存在的输入文件（如果有的话）
        // 注意：不移除 -include 或 -imacros 参数后面的文件
        for (int i = 0; i < command.size(); i++) {
            String arg = command.get(i);
            // 如果是 -include 或 -imacros 的下一个参数，跳过
            if (i > 0 && ("-include".equals(command.get(i - 1)) || "-imacros".equals(command.get(i - 1)))) {
                continue;
            }
            // 移除独立的 .c/.h/.cpp 文件
            if (arg.endsWith(".c") || arg.endsWith(".h") || arg.endsWith(".cpp")) {
                command.remove(i);
                i--; // 调整索引
            }
        }
        
        // 添加当前文件
        command.add(file.toAbsolutePath().toString());
        
        logger.debug("Executing command: {}", String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // 读取输出
        String output;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        
        // 等待进程完成
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Process interrupted for file: {}", file);
            return new PreprocessResult("", List.of("Process interrupted"), -1);
        }
        
        // 如果有错误，尝试从输出中提取
        List<String> errorList = new ArrayList<>();
        if (exitCode != 0) {
            String errorMsg = "GCC preprocessing failed with exit code: " + exitCode;
            logger.error(errorMsg);
            errorList.add(errorMsg);
            
            if (!output.isEmpty()) {
                String outputPreview = output.substring(0, Math.min(output.length(), 500));
                logger.error("GCC output: {}", outputPreview);
                errorList.add("Output: " + outputPreview);
            }
        } else {
            // 记录预处理后的内容到单独的日志文件
            logger.debug("Preprocessing successful, output length: {} chars", output.length());
            preprocessLogger.debug("=== Preprocessed content for: {} ===", file.getFileName());
            preprocessLogger.debug(output);
            preprocessLogger.debug("=== End of preprocessed content ===\n");
        }
        
        return new PreprocessResult(output, errorList, exitCode);
    }
    
    /**
     * 检查 GCC 是否可用
     */
    public static boolean isGccAvailable() {
        try {
            Process process = new ProcessBuilder("gcc", "--version").start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
    
    /**
     * 获取 GCC 版本信息
     */
    public static String getGccVersion() {
        try {
            Process process = new ProcessBuilder("gcc", "--version").start();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line : "Unknown";
            }
        } catch (IOException | InterruptedException e) {
            return "Not available";
        }
    }
    
    public record PreprocessResult(String content, List<String> errors, int exitCode) {
        public boolean hasErrors() {
            return exitCode != 0 || !errors.isEmpty();
        }
    }
}
