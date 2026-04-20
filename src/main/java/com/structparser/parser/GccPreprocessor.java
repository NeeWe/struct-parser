package com.structparser.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * GCC 预处理器 - 使用 gcc -E 进行标准 C 预处理
 */
public class GccPreprocessor {
    
    private List<String> preprocessCommand = null;
    
    /**
     * 从编译配置文件加载预处理命令
     * 支持多种格式：
     * 1. compile_commands.json (JSON Compilation Database)
     * 2. Makefile (提取 CFLAGS/CPPFLAGS)
     * 3. 直接指定 gcc 命令和参数
     */
    public GccPreprocessor loadCompileConfig(Path configFile) throws IOException {
        String content = Files.readString(configFile);
        String fileName = configFile.getFileName().toString().toLowerCase();
        
        if (fileName.endsWith(".json")) {
            // JSON Compilation Database 格式
            preprocessCommand = parseJsonCompilationDatabase(content);
        } else if (fileName.endsWith(".mk") || fileName.equals("makefile") || fileName.equals("Makefile")) {
            // Makefile 格式
            preprocessCommand = parseMakefile(content);
        } else {
            // 假设是直接命令
            preprocessCommand = parseDirectCommand(content);
        }
        
        if (preprocessCommand == null || preprocessCommand.isEmpty()) {
            throw new IOException("Failed to extract preprocessing command from: " + configFile);
        }
        
        return this;
    }
    
    /**
     * 解析 JSON Compilation Database
     */
    private List<String> parseJsonCompilationDatabase(String jsonContent) {
        // 简化实现：查找第一个条目的 command 字段
        // 实际应该使用 JSON 解析器
        Pattern pattern = Pattern.compile("\"command\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(jsonContent);
        
        if (matcher.find()) {
            String command = matcher.group(1);
            return buildPreprocessCommand(command);
        }
        
        return null;
    }
    
    /**
     * 解析 Makefile，提取 CFLAGS/CPPFLAGS
     */
    private List<String> parseMakefile(String makefileContent) {
        // 查找 CFLAGS 或 CPPFLAGS
        Pattern cflagsPattern = Pattern.compile("CFLAGS\\s*[+]?=\\s*(.+)", Pattern.MULTILINE);
        Pattern cppflagsPattern = Pattern.compile("CPPFLAGS\\s*[+]?=\\s*(.+)", Pattern.MULTILINE);
        
        Matcher cflagsMatcher = cflagsPattern.matcher(makefileContent);
        Matcher cppflagsMatcher = cppflagsPattern.matcher(makefileContent);
        
        StringBuilder flags = new StringBuilder();
        
        if (cppflagsMatcher.find()) {
            flags.append(cppflagsMatcher.group(1).trim()).append(" ");
        }
        
        if (cflagsMatcher.find()) {
            flags.append(cflagsMatcher.group(1).trim());
        }
        
        if (flags.length() > 0) {
            String command = "gcc " + flags.toString();
            return buildPreprocessCommand(command);
        }
        
        return null;
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
