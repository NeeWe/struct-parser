package com.structparser.parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GCC 预处理器 - 使用 gcc -E 进行标准 C 预处理
 */
public class GccPreprocessor {
    
    private final List<String> includePaths = new ArrayList<>();
    private String gccCommand = "gcc";
    
    /**
     * 添加包含路径
     */
    public GccPreprocessor addIncludePath(Path path) {
        includePaths.add(path.toAbsolutePath().toString());
        return this;
    }
    
    /**
     * 设置 GCC 命令（用于自定义交叉编译器）
     */
    public GccPreprocessor setGccCommand(String command) {
        this.gccCommand = command;
        return this;
    }
    
    /**
     * 预处理文件
     */
    public PreprocessResult preprocess(Path file) throws IOException {
        var command = new ArrayList<String>();
        command.add(gccCommand);
        command.add("-E");           // 仅预处理
        command.add("-P");           // 禁止行标记
        command.add("-C");           // 保留注释
        command.add("-nostdinc");    // 不包含标准头文件路径
        
        // 添加自定义包含路径
        for (String path : includePaths) {
            command.add("-I" + path);
        }
        
        command.add(file.toAbsolutePath().toString());
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // 读取输出
        String output;
        String errors;
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
