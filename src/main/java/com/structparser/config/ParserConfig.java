package com.structparser.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析器配置类 - 从 YAML/Properties 文件加载
 */
public record ParserConfig(
    String headerFile,
    List<String> includePaths,
    String gccCommand,
    boolean gccRequired,
    OutputConfig output
) {
    
    public ParserConfig {
        includePaths = includePaths != null ? List.copyOf(includePaths) : List.of();
        gccCommand = gccCommand != null ? gccCommand : "gcc";
        gccRequired = gccRequired; // 强制要求 GCC
        output = output != null ? output : new OutputConfig("json", null);
    }
    
    /**
     * 创建默认配置
     */
    public static ParserConfig defaults() {
        return new ParserConfig(
            null,
            new ArrayList<>(),
            "gcc",
            true,  // 默认强制使用 GCC
            new OutputConfig("json", null)
        );
    }
    
    /**
     * 获取头文件路径
     */
    public Path getHeaderFilePath() {
        return headerFile != null ? Paths.get(headerFile) : null;
    }
    
    /**
     * 获取包含路径列表
     */
    public List<Path> getIncludePaths() {
        return includePaths.stream()
            .map(Paths::get)
            .toList();
    }
    
    /**
     * 验证配置是否有效
     */
    public void validate() {
        if (headerFile == null || headerFile.isBlank()) {
            throw new IllegalStateException("Header file path must be specified in configuration");
        }
        
        Path path = getHeaderFilePath();
        if (!java.nio.file.Files.exists(path)) {
            throw new IllegalStateException("Header file does not exist: " + headerFile);
        }
    }
    
    /**
     * 输出配置
     */
    public record OutputConfig(String format, String outputFile) {
        public OutputConfig {
            format = format != null ? format : "json";
        }
    }
}
