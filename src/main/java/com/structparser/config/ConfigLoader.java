package com.structparser.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置加载器 - 支持 YAML 和 Properties 格式
 */
public class ConfigLoader {
    
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    
    /**
     * 从文件加载配置
     */
    public static ParserConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("Configuration file not found: " + configPath);
        }
        
        String fileName = configPath.getFileName().toString().toLowerCase();
        
        try (InputStream is = Files.newInputStream(configPath)) {
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                return YAML_MAPPER.readValue(is, ParserConfig.class);
            } else if (fileName.endsWith(".json")) {
                return JSON_MAPPER.readValue(is, ParserConfig.class);
            } else {
                // 默认尝试 YAML
                return YAML_MAPPER.readValue(is, ParserConfig.class);
            }
        }
    }
    
    /**
     * 从类路径加载配置
     */
    public static ParserConfig loadFromClasspath(String resourcePath) throws IOException {
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Configuration resource not found: " + resourcePath);
            }
            
            String lowerPath = resourcePath.toLowerCase();
            if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
                return YAML_MAPPER.readValue(is, ParserConfig.class);
            } else if (lowerPath.endsWith(".json")) {
                return JSON_MAPPER.readValue(is, ParserConfig.class);
            } else {
                return YAML_MAPPER.readValue(is, ParserConfig.class);
            }
        }
    }
    
    /**
     * 自动查找配置文件
     * 查找顺序: struct-parser.yaml -> struct-parser.yml -> struct-parser.json
     */
    public static ParserConfig autoLoad(Path directory) throws IOException {
        String[] configFiles = {
            "struct-parser.yaml",
            "struct-parser.yml",
            "struct-parser.json"
        };
        
        for (String fileName : configFiles) {
            Path configPath = directory.resolve(fileName);
            if (Files.exists(configPath)) {
                return load(configPath);
            }
        }
        
        // 尝试从类路径加载
        for (String fileName : configFiles) {
            try {
                return loadFromClasspath(fileName);
            } catch (IOException e) {
                // 继续尝试下一个
            }
        }
        
        throw new IOException(
            "No configuration file found. Expected one of: " +
            String.join(", ", configFiles) +
            " in directory: " + directory
        );
    }
    
    /**
     * 保存配置到文件（用于生成示例配置）
     */
    public static void save(ParserConfig config, Path configPath) throws IOException {
        String fileName = configPath.getFileName().toString().toLowerCase();
        
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            YAML_MAPPER.writeValue(configPath.toFile(), config);
        } else {
            JSON_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(configPath.toFile(), config);
        }
    }
}
