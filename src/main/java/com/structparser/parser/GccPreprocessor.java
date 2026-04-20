package com.structparser.parser;

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
    
    private List<String> preprocessCommand = null;
    
    /**
     * 从编译配置文件加载预处理命令
     * 仅支持直接命令文件格式（类C DSL的gcc命令）
     */
    public GccPreprocessor loadCompileConfig(Path configFile) throws IOException {
        String content = Files.readString(configFile);
        
        // 解析直接命令
        preprocessCommand = parseDirectCommand(content);
        
        if (preprocessCommand == null || preprocessCommand.isEmpty()) {
            throw new IOException("Failed to extract preprocessing command from: " + configFile);
        }
        
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
            throw new IllegalStateException("Compile config not loaded. Call loadCompileConfig() first.");
        }
        
        // 复制命令并添加输入文件
        var command = new ArrayList<>(preprocessCommand);
        
        // 移除可能存在的输入文件（如果有的话）
        command.removeIf(arg -> arg.endsWith(".c") || arg.endsWith(".h") || arg.endsWith(".cpp"));
        
        // 添加当前文件
        command.add(file.toAbsolutePath().toString());
        
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
            return new PreprocessResult("", List.of("Process interrupted"), -1);
        }
        
        // 如果有错误，尝试从输出中提取
        List<String> errorList = new ArrayList<>();
        if (exitCode != 0) {
            errorList.add("GCC preprocessing failed with exit code: " + exitCode);
            if (!output.isEmpty()) {
                errorList.add("Output: " + output.substring(0, Math.min(output.length(), 500)));
            }
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
