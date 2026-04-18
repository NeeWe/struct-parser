package com.structparser.integration;

import com.structparser.config.ConfigLoader;
import com.structparser.config.ParserConfig;
import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import com.structparser.parser.GccPreprocessor;
import com.structparser.parser.StructParserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GCC 预处理集成测试 - 完整解析流程
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GccIntegrationTest {
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        assumeTrue(GccPreprocessor.isGccAvailable(), "GCC not available, skipping integration tests");
    }
    
    // ========== 完整流程测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("完整流程：配置 -> GCC预处理 -> 解析 -> JSON输出")
    void testFullParseFlow() throws IOException {
        // 1. 创建测试头文件
        Path headerFile = createHeaderFile("device.h", """
            #ifndef DEVICE_H
            #define DEVICE_H
            
            #define REG_BASE 0x40000000
            
            struct ControlReg {
                uint1 enable;
                uint1 interrupt;
                uint2 mode;
                uint4 reserved;
                uint8 prescale;
                uint16 timeout;
            };
            
            #endif
            """);
        
        // 2. 创建配置文件
        Path configFile = createConfigFile("config.yaml", headerFile, List.of());
        
        // 3. 加载配置
        ParserConfig config = ConfigLoader.load(configFile);
        config.validate();
        
        // 4. 解析（强制使用 GCC 预处理）
        var service = new StructParserService()
            .enableGccPreprocessing()
            .setGccCommand(config.gccCommand());
        
        for (Path path : config.getIncludePaths()) {
            service.addSearchPath(path);
        }
        
        ParseResult result = service.parseFile(config.getHeaderFilePath());
        
        // 5. 生成 JSON
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 6. 验证结果
        assertFalse(result.hasErrors(), "Should not have parsing errors: " + result.errors());
        assertNotNull(json, "JSON output should not be null");
        assertTrue(json.contains("ControlReg"), "Should contain ControlReg");
        assertTrue(json.contains("enable"), "Should contain enable field");
        assertEquals(1, result.structs().size(), "Should have 1 struct");
        assertEquals(32, result.structs().get(0).totalBits(), "ControlReg should be 32 bits");
    }
    
    @Test
    @Order(2)
    @DisplayName("多文件包含完整流程")
    void testMultiFileIncludeFlow() throws IOException {
        // 创建目录结构
        Path includeDir = tempDir.resolve("include");
        Files.createDirectories(includeDir);
        
        // 创建类型定义头文件
        Files.writeString(includeDir.resolve("types.h"), """
            #ifndef TYPES_H
            #define TYPES_H
            
            struct Version {
                uint8 major;
                uint8 minor;
                uint16 build;
            };
            
            #endif
            """);
        
        // 创建主头文件
        Path headerFile = tempDir.resolve("main.h");
        Files.writeString(headerFile, """
            #ifndef MAIN_H
            #define MAIN_H
            
            #include "types.h"
            
            struct Device {
                struct Version version;
                uint32 status;
            };
            
            #endif
            """);
        
        // 创建配置文件
        Path configFile = createConfigFile("multi.yaml", headerFile, List.of(includeDir.toString()));
        
        // 执行解析流程
        ParserConfig config = ConfigLoader.load(configFile);
        var service = new StructParserService()
            .enableGccPreprocessing()
            .setGccCommand(config.gccCommand());
        
        for (Path path : config.getIncludePaths()) {
            service.addSearchPath(path);
        }
        
        ParseResult result = service.parseFile(config.getHeaderFilePath());
        
        // 验证
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertEquals(2, result.structs().size(), "Should have 2 structs (Version and Device)");
        
        // 验证 Device 包含 Version 的位宽
        var device = result.getStructByName("Device");
        assertNotNull(device, "Device struct should exist");
        assertEquals(64, device.totalBits(), "Device should be 64 bits (32 + 32)");
    }
    
    @Test
    @Order(3)
    @DisplayName("复杂宏展开完整流程")
    void testComplexMacroExpansion() throws IOException {
        Path headerFile = createHeaderFile("macros.h", """
            #ifndef MACROS_H
            #define MACROS_H
            
            #define FIELD(name, width) uint##width name
            #define REG_FIELD(name, offset, width) \
                FIELD(name, width)
            
            #define VERSION 2
            
            struct ConfigReg {
            #if VERSION >= 2
                REG_FIELD(advanced, 0, 32);
            #else
                REG_FIELD(basic, 0, 16);
            #endif
            };
            
            #endif
            """);
        
        Path configFile = createConfigFile("macro.yaml", headerFile, List.of());
        
        ParserConfig config = ConfigLoader.load(configFile);
        var service = new StructParserService()
            .enableGccPreprocessing()
            .setGccCommand(config.gccCommand());
        
        ParseResult result = service.parseFile(config.getHeaderFilePath());
        
        assertFalse(result.hasErrors(), "Should not have errors");
        var configReg = result.getStructByName("ConfigReg");
        assertNotNull(configReg, "ConfigReg should exist");
        
        // VERSION >= 2，所以应该有 advanced 字段（32位）
        assertEquals(32, configReg.totalBits(), "ConfigReg should be 32 bits with advanced field");
    }
    
    @Test
    @Order(4)
    @DisplayName("条件编译完整流程")
    void testConditionalCompilation() throws IOException {
        Path headerFile = createHeaderFile("conditional.h", """
            #ifndef CONDITIONAL_H
            #define CONDITIONAL_H
            
            #define DEBUG_BUILD
            #define FEATURE_LEVEL 3
            
            struct Features {
                uint32 core;
                
            #ifdef DEBUG_BUILD
                uint32 debug_info;
            #endif
                
            #if FEATURE_LEVEL >= 2
                uint32 advanced_feature;
            #endif
                
            #if FEATURE_LEVEL >= 3
                uint32 premium_feature;
            #endif
            };
            
            #endif
            """);
        
        Path configFile = createConfigFile("conditional.yaml", headerFile, List.of());
        
        ParserConfig config = ConfigLoader.load(configFile);
        var service = new StructParserService()
            .enableGccPreprocessing()
            .setGccCommand(config.gccCommand());
        
        ParseResult result = service.parseFile(config.getHeaderFilePath());
        
        assertFalse(result.hasErrors(), "Should not have errors");
        var features = result.getStructByName("Features");
        assertNotNull(features, "Features struct should exist");
        
        // core(32) + debug_info(32) + advanced_feature(32) + premium_feature(32) = 128 bits
        assertEquals(128, features.totalBits(), 
            "Features should include all conditional fields (128 bits)");
    }
    
    @Test
    @Order(5)
    @DisplayName("嵌套结构体与宏完整流程")
    void testNestedStructsWithMacros() throws IOException {
        Path headerFile = createHeaderFile("nested.h", """
            #ifndef NESTED_H
            #define NESTED_H
            
            #define PACKED __attribute__((packed))
            
            struct Inner {
                uint8 a;
                uint8 b;
            };
            
            struct Outer {
                struct Inner inner;
                uint16 extra;
            };
            
            #endif
            """);
        
        Path configFile = createConfigFile("nested.yaml", headerFile, List.of());
        
        ParserConfig config = ConfigLoader.load(configFile);
        var service = new StructParserService()
            .enableGccPreprocessing()
            .setGccCommand(config.gccCommand());
        
        ParseResult result = service.parseFile(config.getHeaderFilePath());
        
        assertFalse(result.hasErrors(), "Should not have errors");
        assertEquals(2, result.structs().size(), "Should have 2 structs");
        
        var inner = result.getStructByName("Inner");
        var outer = result.getStructByName("Outer");
        
        assertNotNull(inner, "Inner should exist");
        assertNotNull(outer, "Outer should exist");
        assertEquals(16, inner.totalBits(), "Inner should be 16 bits");
        assertEquals(32, outer.totalBits(), "Outer should be 32 bits (16 + 16)");
    }
    
    @Test
    @Order(6)
    @DisplayName("错误处理：GCC 不可用")
    void testGccNotAvailable() {
        // 模拟 GCC 不可用的情况
        if (!GccPreprocessor.isGccAvailable()) {
            var service = new StructParserService();
            
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.enableGccPreprocessing(),
                "Should handle GCC not available"
            );
            
            // 实际行为是 parseFile 时检查
            var result = service.parse("struct Test { uint8 a; };");
            // 字符串解析不依赖 GCC
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("错误处理：无效的头文件")
    void testInvalidHeaderFile() throws IOException {
        Path configFile = createConfigFile("invalid.yaml", 
            tempDir.resolve("non_existent.h"), List.of());
        
        ParserConfig config = ConfigLoader.load(configFile);
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            config::validate,
            "Should throw for non-existent header file"
        );
        
        assertTrue(exception.getMessage().contains("does not exist"));
    }
    
    @Test
    @Order(8)
    @DisplayName("错误处理：GCC 预处理失败")
    void testGccPreprocessFailure() throws IOException {
        // 创建包含语法错误的头文件（GCC 无法处理的）
        Path headerFile = createHeaderFile("bad_syntax.h", """
            #include <non_existent_system_header.h>
            
            struct Test {
                uint32 value;
            };
            """);
        
        Path configFile = createConfigFile("bad.yaml", headerFile, List.of());
        
        ParserConfig config = ConfigLoader.load(configFile);
        var service = new StructParserService()
            .enableGccPreprocessing()
            .setGccCommand(config.gccCommand());
        
        ParseResult result = service.parseFile(config.getHeaderFilePath());
        
        // 应该报告错误
        assertTrue(result.hasErrors(), "Should report errors for missing system header");
    }
    
    @Test
    @Order(9)
    @DisplayName("输出到文件完整流程")
    void testOutputToFile() throws IOException {
        Path headerFile = createHeaderFile("output_test.h", """
            struct OutputTest {
                uint8 field;
            };
            """);
        
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("result.json");
        
        // 创建带输出文件路径的配置
        Path configFile = tempDir.resolve("output_config.yaml");
        Files.writeString(configFile, String.format("""
            headerFile: %s
            gccCommand: gcc
            gccRequired: true
            output:
              format: json
              outputFile: %s
            """, headerFile, outputFile));
        
        ParserConfig config = ConfigLoader.load(configFile);
        var service = new StructParserService()
            .enableGccPreprocessing()
            .setGccCommand(config.gccCommand());
        
        ParseResult result = service.parseFile(config.getHeaderFilePath());
        
        // 生成并保存输出
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        Files.writeString(outputFile, json);
        
        // 验证文件存在
        assertTrue(Files.exists(outputFile), "Output file should exist");
        String content = Files.readString(outputFile);
        assertTrue(content.contains("OutputTest"), "Output should contain struct name");
    }
    
    // ========== 辅助方法 ==========
    
    private Path createHeaderFile(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }
    
    private Path createConfigFile(String name, Path headerFile, List<String> includePaths) throws IOException {
        Path path = tempDir.resolve(name);
        
        StringBuilder sb = new StringBuilder();
        sb.append("headerFile: ").append(headerFile.toString().replace("\\", "/")).append("\n");
        sb.append("gccCommand: gcc\n");
        sb.append("gccRequired: true\n");
        
        if (!includePaths.isEmpty()) {
            sb.append("includePaths:\n");
            for (String inc : includePaths) {
                sb.append("  - ").append(inc.replace("\\", "/")).append("\n");
            }
        }
        
        sb.append("output:\n");
        sb.append("  format: json\n");
        
        Files.writeString(path, sb.toString());
        return path;
    }
}
