package com.structparser.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 解析器配置类 - 从 YAML/Properties 文件加载
 */
public record ParserConfig(
    String compileConfigFile,
    OutputConfig output
) {
    
    public ParserConfig {
        output = output != null ? output : new OutputConfig("json", null);
    }
    
    /**
     * 创建默认配置
     */
    public static ParserConfig defaults() {
        return new ParserConfig(
            null,
            new OutputConfig("json", null)
        );
    }
    
    /**
     * 验证配置是否有效
     */
    public void validate() {
        if (compileConfigFile == null || compileConfigFile.isEmpty()) {
            throw new IllegalStateException("compileConfigFile must be specified in configuration");
        }
        
        Path configPath = Paths.get(compileConfigFile);
        if (!Files.exists(configPath)) {
            throw new IllegalStateException("Compile config file does not exist: " + compileConfigFile);
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
