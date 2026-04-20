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
            compileConfigFile: ./compile_commands.json
            output:
              format: json
              outputFile: output/result.json
            """);
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        assertEquals("./compile_commands.json", config.compileConfigFile());
        assertEquals("json", config.output().format());
        assertEquals("output/result.json", config.output().outputFile());
    }
    
    @Test
    @Order(2)
    @DisplayName("从 YML 文件加载配置")
    void testLoadYmlConfig() throws IOException {
        Path configFile = tempDir.resolve("test.yml");
        Files.writeString(configFile, """
            compileConfigFile: Makefile
            """);
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        assertEquals("Makefile", config.compileConfigFile());
    }
    
    // ========== JSON 配置测试 ==========
    
    @Test
    @Order(10)
    @DisplayName("从 JSON 文件加载配置")
    void testLoadJsonConfig() throws IOException {
        Path configFile = tempDir.resolve("test.json");
        Files.writeString(configFile, """
            {
              "compileConfigFile": "compile_commands.json",
              "output": {
                "format": "json",
                "outputFile": "out.json"
              }
            }
            """);
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        assertEquals("compile_commands.json", config.compileConfigFile());
    }
    
    // ========== 默认值测试 ==========
    
    @Test
    @Order(20)
    @DisplayName("配置默认值")
    void testConfigDefaults() {
        ParserConfig config = ParserConfig.defaults();
        
        assertNull(config.compileConfigFile());
        assertNotNull(config.output());
        assertEquals("json", config.output().format());
    }
    
    @Test
    @Order(21)
    @DisplayName("部分配置使用默认值")
    void testPartialConfigWithDefaults() throws IOException {
        Path configFile = tempDir.resolve("partial.yaml");
        Files.writeString(configFile, """
            compileConfigFile: ./command.txt
            """);
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        assertEquals("./command.txt", config.compileConfigFile());
        assertNotNull(config.output(), "Output config should have defaults");
        assertEquals("json", config.output().format(), "Output format should default to 'json'");
    }
    
    // ========== 配置验证测试 ==========
    
    @Test
    @Order(30)
    @DisplayName("验证有效配置")
    void testValidateValidConfig() throws IOException {
        Path compileConfig = tempDir.resolve("compile_commands.json");
        Files.writeString(compileConfig, "[]");
        
        ParserConfig config = new ParserConfig(
            compileConfig.toString(),
            null
        );
        
        assertDoesNotThrow(config::validate, "Valid config should not throw");
    }
    
    @Test
    @Order(31)
    @DisplayName("验证缺失编译配置文件")
    void testValidateMissingCompileConfigFile() {
        ParserConfig config = ParserConfig.defaults();
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            config::validate,
            "Should throw for missing compile config file"
        );
        assertTrue(exception.getMessage().contains("compileConfigFile"));
    }
    
    @Test
    @Order(32)
    @DisplayName("验证不存在的编译配置文件")
    void testValidateNonExistentCompileConfigFile() {
        ParserConfig config = new ParserConfig(
            "/path/that/does/not/exist.json",
            null
        );
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            config::validate,
            "Should throw for non-existent compile config file"
        );
        assertTrue(exception.getMessage().contains("does not exist"));
    }
    
    // ========== 自动加载测试 ==========
    
    @Test
    @Order(40)
    @DisplayName("自动加载 YAML 配置")
    void testAutoLoadYaml() throws IOException {
        Files.writeString(tempDir.resolve("struct-parser.yaml"), """
            compileConfigFile: ./compile_commands.json
            """);
        
        ParserConfig config = ConfigLoader.autoLoad(tempDir);
        
        assertEquals("./compile_commands.json", config.compileConfigFile());
    }
    
    @Test
    @Order(41)
    @DisplayName("自动加载 YML 配置（YAML 不存在时）")
    void testAutoLoadYml() throws IOException {
        Files.writeString(tempDir.resolve("struct-parser.yml"), """
            compileConfigFile: Makefile
            """);
        
        ParserConfig config = ConfigLoader.autoLoad(tempDir);
        
        assertEquals("Makefile", config.compileConfigFile());
    }
    
    @Test
    @Order(42)
    @DisplayName("自动加载 JSON 配置（YAML/YML 不存在时）")
    void testAutoLoadJson() throws IOException {
        Files.writeString(tempDir.resolve("struct-parser.json"), """
            {"compileConfigFile": "compile_commands.json"}
            """);
        
        ParserConfig config = ConfigLoader.autoLoad(tempDir);
        
        assertEquals("compile_commands.json", config.compileConfigFile());
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
            "compile_commands.json",
            new ParserConfig.OutputConfig("json", "out.json")
        );
        
        ConfigLoader.save(config, configFile);
        
        assertTrue(Files.exists(configFile), "Config file should be created");
        String content = Files.readString(configFile);
        assertTrue(content.contains("compile_commands.json"), "Should contain compile config file");
    }
    
    @Test
    @Order(51)
    @DisplayName("保存配置到 JSON 文件")
    void testSaveJsonConfig() throws IOException {
        Path configFile = tempDir.resolve("saved.json");
        ParserConfig config = new ParserConfig(
            "Makefile",
            null
        );
        
        ConfigLoader.save(config, configFile);
        
        assertTrue(Files.exists(configFile), "Config file should be created");
        String content = Files.readString(configFile);
        assertTrue(content.contains("Makefile"), "Should contain compile config file");
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
