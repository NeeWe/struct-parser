package com.structparser.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析器配置类 - 从 YAML/Properties 文件加载
 */
public record ParserConfig(
    List<String> includePaths,
    String gccCommand,
    boolean gccRequired,
    OutputConfig output
) {
    
    public ParserConfig {
        includePaths = includePaths != null ? List.copyOf(includePaths) : List.of();
        gccCommand = gccCommand != null ? gccCommand : "gcc";
        gccRequired = gccRequired;
        output = output != null ? output : new OutputConfig("json", null);
    }
    
    /**
     * 创建默认配置
     */
    public static ParserConfig defaults() {
        return new ParserConfig(
            new ArrayList<>(),
            "gcc",
            true,
            new OutputConfig("json", null)
        );
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
        if (includePaths == null || includePaths.isEmpty()) {
            throw new IllegalStateException("includePaths must be specified in configuration");
        }
        
        for (String pathStr : includePaths) {
            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                throw new IllegalStateException("Include path does not exist: " + pathStr);
            }
            if (!Files.isDirectory(path)) {
                throw new IllegalStateException("Include path is not a directory: " + pathStr);
            }
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
