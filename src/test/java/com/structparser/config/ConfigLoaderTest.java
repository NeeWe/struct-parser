package com.structparser.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置加载器全面测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConfigLoaderTest {
    
    @TempDir
    Path tempDir;
    
    // ========== YAML 配置测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("从 YAML 文件加载配置")
    void testLoadYamlConfig() throws IOException {
        Path configFile = tempDir.resolve("test.yaml");
        Files.writeString(configFile, """
            headerFile: src/test.h
            includePaths:
              - ./include
              - ./drivers
            gccCommand: gcc
            gccRequired: true
            output:
              format: json
              outputFile: output/result.json
            """);
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        assertEquals("src/test.h", config.headerFile());
        assertEquals(List.of("./include", "./drivers"), config.includePaths());
        assertEquals("gcc", config.gccCommand());
        assertTrue(config.gccRequired());
        assertEquals("json", config.output().format());
        assertEquals("output/result.json", config.output().outputFile());
    }
    
    @Test
    @Order(2)
    @DisplayName("从 YML 文件加载配置")
    void testLoadYmlConfig() throws IOException {
        Path configFile = tempDir.resolve("test.yml");
        Files.writeString(configFile, """
            headerFile: src/main.h
            gccCommand: arm-none-eabi-gcc
            gccRequired: true
            """);
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        assertEquals("src/main.h", config.headerFile());
        assertEquals("arm-none-eabi-gcc", config.gccCommand());
        assertTrue(config.gccRequired());
    }
    
    // ========== JSON 配置测试 ==========
    
    @Test
    @Order(10)
    @DisplayName("从 JSON 文件加载配置")
    void testLoadJsonConfig() throws IOException {
        Path configFile = tempDir.resolve("test.json");
        Files.writeString(configFile, """
            {
              "headerFile": "src/device.h",
              "includePaths": ["./inc", "./hal"],
              "gccCommand": "gcc",
              "gccRequired": true,
              "output": {
                "format": "json",
                "outputFile": "out.json"
              }
            }
            """);
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        assertEquals("src/device.h", config.headerFile());
        assertEquals(2, config.includePaths().size());
        assertEquals("./inc", config.includePaths().get(0));
    }
    
    // ========== 默认值测试 ==========
    
    @Test
    @Order(20)
    @DisplayName("配置默认值")
    void testConfigDefaults() {
        ParserConfig config = ParserConfig.defaults();
        
        assertNull(config.headerFile());
        assertTrue(config.includePaths().isEmpty());
        assertEquals("gcc", config.gccCommand());
        assertTrue(config.gccRequired()); // 默认强制使用 GCC
        assertNotNull(config.output());
        assertEquals("json", config.output().format());
    }
    
    @Test
    @Order(21)
    @DisplayName("部分配置使用默认值")
    void testPartialConfigWithDefaults() throws IOException {
        Path configFile = tempDir.resolve("partial.yaml");
        Files.writeString(configFile, """
            headerFile: src/test.h
            gccRequired: true
            """);
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        assertEquals("src/test.h", config.headerFile());
        assertTrue(config.includePaths().isEmpty(), "Include paths should default to empty");
        assertEquals("gcc", config.gccCommand(), "GCC command should default to 'gcc'");
        assertTrue(config.gccRequired(), "GCC required should default to true");
    }
    
    // ========== 配置验证测试 ==========
    
    @Test
    @Order(30)
    @DisplayName("验证有效配置")
    void testValidateValidConfig() throws IOException {
        Path headerFile = tempDir.resolve("test.h");
        Files.createFile(headerFile);
        
        ParserConfig config = new ParserConfig(
            headerFile.toString(),
            List.of(),
            "gcc",
            true,
            null
        );
        
        assertDoesNotThrow(config::validate, "Valid config should not throw");
    }
    
    @Test
    @Order(31)
    @DisplayName("验证缺失头文件路径")
    void testValidateMissingHeaderFile() {
        ParserConfig config = ParserConfig.defaults();
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            config::validate,
            "Should throw for missing header file"
        );
        assertTrue(exception.getMessage().contains("Header file path"));
    }
    
    @Test
    @Order(32)
    @DisplayName("验证不存在的头文件")
    void testValidateNonExistentHeaderFile() {
        ParserConfig config = new ParserConfig(
            "/path/that/does/not/exist.h",
            List.of(),
            "gcc",
            true,
            null
        );
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            config::validate,
            "Should throw for non-existent header file"
        );
        assertTrue(exception.getMessage().contains("does not exist"));
    }
    
    // ========== 自动加载测试 ==========
    
    @Test
    @Order(40)
    @DisplayName("自动加载 YAML 配置")
    void testAutoLoadYaml() throws IOException {
        Files.writeString(tempDir.resolve("struct-parser.yaml"), """
            headerFile: src/auto.yaml
            gccRequired: true
            """);
        
        ParserConfig config = ConfigLoader.autoLoad(tempDir);
        
        assertEquals("src/auto.yaml", config.headerFile());
    }
    
    @Test
    @Order(41)
    @DisplayName("自动加载 YML 配置（YAML 不存在时）")
    void testAutoLoadYml() throws IOException {
        Files.writeString(tempDir.resolve("struct-parser.yml"), """
            headerFile: src/auto.yml
            gccRequired: true
            """);
        
        ParserConfig config = ConfigLoader.autoLoad(tempDir);
        
        assertEquals("src/auto.yml", config.headerFile());
    }
    
    @Test
    @Order(42)
    @DisplayName("自动加载 JSON 配置（YAML/YML 不存在时）")
    void testAutoLoadJson() throws IOException {
        Files.writeString(tempDir.resolve("struct-parser.json"), """
            {"headerFile": "src/auto.json", "gccRequired": true}
            """);
        
        ParserConfig config = ConfigLoader.autoLoad(tempDir);
        
        assertEquals("src/auto.json", config.headerFile());
    }
    
    @Test
    @Order(43)
    @DisplayName("自动加载无配置文件时抛出异常")
    void testAutoLoadNoConfig() {
        IOException exception = assertThrows(
            IOException.class,
            () -> ConfigLoader.autoLoad(tempDir),
            "Should throw when no config file found"
        );
        assertTrue(exception.getMessage().contains("No configuration file found"));
    }
    
    // ========== 配置保存测试 ==========
    
    @Test
    @Order(50)
    @DisplayName("保存配置到 YAML 文件")
    void testSaveYamlConfig() throws IOException {
        Path configFile = tempDir.resolve("saved.yaml");
        ParserConfig config = new ParserConfig(
            "src/saved.h",
            List.of("./inc"),
            "arm-gcc",
            true,
            new ParserConfig.OutputConfig("json", "out.json")
        );
        
        ConfigLoader.save(config, configFile);
        
        assertTrue(Files.exists(configFile), "Config file should be created");
        String content = Files.readString(configFile);
        assertTrue(content.contains("src/saved.h"), "Should contain header file");
        assertTrue(content.contains("arm-gcc"), "Should contain GCC command");
    }
    
    @Test
    @Order(51)
    @DisplayName("保存配置到 JSON 文件")
    void testSaveJsonConfig() throws IOException {
        Path configFile = tempDir.resolve("saved.json");
        ParserConfig config = new ParserConfig(
            "src/saved.h",
            List.of(),
            "gcc",
            true,
            null
        );
        
        ConfigLoader.save(config, configFile);
        
        assertTrue(Files.exists(configFile), "Config file should be created");
        String content = Files.readString(configFile);
        assertTrue(content.contains("src/saved.h"), "Should contain header file");
    }
    
    // ========== 路径转换测试 ==========
    
    @Test
    @Order(60)
    @DisplayName("获取头文件路径")
    void testGetHeaderFilePath() {
        ParserConfig config = new ParserConfig(
            "src/test.h",
            List.of(),
            "gcc",
            true,
            null
        );
        
        Path path = config.getHeaderFilePath();
        assertNotNull(path);
        assertEquals("test.h", path.getFileName().toString());
    }
    
    @Test
    @Order(61)
    @DisplayName("获取包含路径列表")
    void testGetIncludePaths() {
        ParserConfig config = new ParserConfig(
            "src/test.h",
            List.of("./inc", "./drivers"),
            "gcc",
            true,
            null
        );
        
        List<Path> paths = config.getIncludePaths();
        assertEquals(2, paths.size());
    }
    
    // ========== 错误处理测试 ==========
    
    @Test
    @Order(70)
    @DisplayName("加载不存在的配置文件")
    void testLoadNonExistentConfig() {
        Path nonExistent = tempDir.resolve("does_not_exist.yaml");
        
        IOException exception = assertThrows(
            IOException.class,
            () -> ConfigLoader.load(nonExistent),
            "Should throw for non-existent config file"
        );
        assertTrue(exception.getMessage().contains("not found"));
    }
    
    @Test
    @Order(71)
    @DisplayName("加载无效的 YAML 内容")
    void testLoadInvalidYaml() throws IOException {
        Path configFile = tempDir.resolve("invalid.yaml");
        Files.writeString(configFile, "this is not: valid: yaml: [");
        
        assertThrows(Exception.class, () -> {
            ConfigLoader.load(configFile);
        }, "Should throw for invalid YAML");
    }
    
    @Test
    @Order(72)
    @DisplayName("加载无效的 JSON 内容")
    void testLoadInvalidJson() throws IOException {
        Path configFile = tempDir.resolve("invalid.json");
        Files.writeString(configFile, "{invalid json}");
        
        assertThrows(Exception.class, () -> {
            ConfigLoader.load(configFile);
        }, "Should throw for invalid JSON");
    }
}
