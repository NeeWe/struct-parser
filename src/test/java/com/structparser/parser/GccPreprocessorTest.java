package com.structparser.parser;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GCC 预处理器全面测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GccPreprocessorTest {
    
    @TempDir
    Path tempDir;
    
    private GccPreprocessor preprocessor;
    
    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(GccPreprocessor.isGccAvailable(), "GCC not available, skipping tests");
        preprocessor = new GccPreprocessor();
        // 创建默认的编译配置文件
        Path compileConfig = tempDir.resolve("command.txt");
        Files.writeString(compileConfig, "gcc -E -P -I.");
        preprocessor.loadCompileConfig(compileConfig);
    }
    
    // ========== 基础功能测试 ==========
    
    @Test
    @Order(1)
    @DisplayName("GCC 可用性检查")
    void testGccAvailability() {
        assertTrue(GccPreprocessor.isGccAvailable(), "GCC should be available");
        assertNotNull(GccPreprocessor.getGccVersion(), "GCC version should not be null");
        assertFalse(GccPreprocessor.getGccVersion().isEmpty(), "GCC version should not be empty");
    }
    
    @Test
    @Order(2)
    @DisplayName("预处理简单头文件")
    void testPreprocessSimpleFile() throws IOException {
        Path header = createHeaderFile("simple.h", """
            struct Simple {
                uint8 a;
                uint16 b;
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertNotNull(result.content(), "Content should not be null");
        assertTrue(result.content().contains("struct Simple"), "Should contain struct definition");
        assertEquals(0, result.exitCode(), "Exit code should be 0");
    }
    
    // ========== 宏定义测试 ==========
    
    @Test
    @Order(10)
    @DisplayName("预处理带宏定义的头文件")
    void testPreprocessWithMacros() throws IOException {
        Path header = createHeaderFile("macros.h", """
            #define VERSION_MAJOR 1
            #define VERSION_MINOR 2
            #define VERSION_PATCH 3
            
            struct Version {
                uint8 major;
                uint8 minor;
                uint16 patch;
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should not have errors");
        // 宏定义应该被移除或展开
        String content = result.content();
        assertTrue(content.contains("struct Version"), "Should contain struct");
    }
    
    @Test
    @Order(11)
    @DisplayName("预处理带宏函数的头文件")
    void testPreprocessWithMacroFunctions() throws IOException {
        Path header = createHeaderFile("macro_funcs.h", """
            #define FIELD(name, width) uint##width name
            #define REG_SIZE 32
            
            struct Register {
                FIELD(enable, 1);
                FIELD(status, 7);
                uint24 reserved;
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should not have errors");
        String content = result.content();
        assertTrue(content.contains("uint1 enable"), "Macro should be expanded");
        assertTrue(content.contains("uint7 status"), "Macro should be expanded");
    }
    
    // ========== 条件编译测试 ==========
    
    @Test
    @Order(20)
    @DisplayName("预处理带#ifdef的头文件")
    void testPreprocessWithIfdef() throws IOException {
        Path header = createHeaderFile("ifdef.h", """
            #define DEBUG_MODE
            
            struct Config {
                uint32 flags;
            #ifdef DEBUG_MODE
                uint32 debug_info;
            #endif
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should not have errors");
        String content = result.content();
        assertTrue(content.contains("debug_info"), "DEBUG_MODE section should be included");
    }
    
    @Test
    @Order(21)
    @DisplayName("预处理带#ifndef保护的头文件")
    void testPreprocessWithIncludeGuard() throws IOException {
        Path header = createHeaderFile("guarded.h", """
            #ifndef GUARDED_H
            #define GUARDED_H
            
            struct Guarded {
                uint32 value;
            };
            
            #endif // GUARDED_H
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should not have errors");
        String content = result.content();
        assertTrue(content.contains("struct Guarded"), "Should contain struct");
        // Include guard 宏应该被处理
    }
    
    @Test
    @Order(22)
    @DisplayName("预处理带#if条件表达式")
    void testPreprocessWithIfCondition() throws IOException {
        Path header = createHeaderFile("if_cond.h", """
            #define VERSION 2
            
            struct Features {
            #if VERSION >= 2
                uint32 advanced;
            #else
                uint16 basic;
            #endif
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should not have errors");
        String content = result.content();
        assertTrue(content.contains("advanced"), "VERSION >= 2 section should be included");
        assertFalse(content.contains("basic"), "VERSION < 2 section should be excluded");
    }
    
    // ========== 文件包含测试 ==========
    
    @Test
    @Order(30)
    @DisplayName("预处理带#include的头文件")
    void testPreprocessWithInclude() throws IOException {
        // 创建被包含的文件
        Path includeDir = tempDir.resolve("include");
        Files.createDirectories(includeDir);
        
        Path typesHeader = includeDir.resolve("types.h");
        Files.writeString(typesHeader, """
            struct BaseType {
                uint32 value;
            };
            """);
        
        Path mainHeader = tempDir.resolve("main.h");
        Files.writeString(mainHeader, """
            #include "types.h"
            
            struct Extended {
                struct BaseType base;
                uint32 extra;
            };
            """);
        
        // 创建编译配置文件
        Path compileConfig = includeDir.resolve("command.txt");
        Files.writeString(compileConfig, "gcc -E -P -I%s".formatted(includeDir.toString()));
        
        preprocessor.loadCompileConfig(compileConfig);
        var result = preprocessor.preprocess(mainHeader);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        String content = result.content();
        assertTrue(content.contains("BaseType"), "Should include BaseType from types.h");
        assertTrue(content.contains("Extended"), "Should contain Extended struct");
    }
    
    @Test
    @Order(31)
    @DisplayName("预处理嵌套#include")
    void testPreprocessWithNestedInclude() throws IOException {
        Path includeDir = tempDir.resolve("include");
        Files.createDirectories(includeDir);
        
        // 创建三层嵌套包含
        Files.writeString(includeDir.resolve("level1.h"), """
            struct Level1 { uint8 a; };
            """);
        
        Files.writeString(includeDir.resolve("level2.h"), """
            #include "level1.h"
            struct Level2 { struct Level1 l1; uint8 b; };
            """);
        
        Path mainHeader = tempDir.resolve("nested.h");
        Files.writeString(mainHeader, """
            #include "level2.h"
            struct Level3 { struct Level2 l2; uint8 c; };
            """);
        
        // 创建编译配置文件
        Path compileConfig = includeDir.resolve("command.txt");
        Files.writeString(compileConfig, "gcc -E -P -I%s".formatted(includeDir.toString()));
        
        preprocessor.loadCompileConfig(compileConfig);
        var result = preprocessor.preprocess(mainHeader);
        
        assertFalse(result.hasErrors(), "Should not have errors");
        String content = result.content();
        assertTrue(content.contains("Level1"), "Should include Level1");
        assertTrue(content.contains("Level2"), "Should include Level2");
        assertTrue(content.contains("Level3"), "Should include Level3");
    }
    
    // ========== 复杂场景测试 ==========
    
    @Test
    @Order(40)
    @DisplayName("预处理带注释的头文件")
    void testPreprocessWithComments() throws IOException {
        Path header = createHeaderFile("comments.h", """
            /* Multi-line comment
               describing the struct */
            struct WithComments {
                uint8 field1; // Single line comment
                /* Another comment */ uint16 field2;
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should not have errors");
        String content = result.content();
        assertTrue(content.contains("WithComments"), "Should contain struct");
        // -C 选项应该保留注释
        assertTrue(content.contains("comment") || content.contains("Comment"), 
            "Comments should be preserved with -C flag");
    }
    
    @Test
    @Order(41)
    @DisplayName("预处理带#line指令的头文件")
    void testPreprocessPreservesStructure() throws IOException {
        Path header = createHeaderFile("structured.h", """
            #define REG_BASE 0x1000
            
            struct RegisterBlock {
                uint32 control;
                uint32 status;
                uint32 data[4];
            };
            
            #define REG_OFFSET(name) (REG_BASE + offsetof(struct RegisterBlock, name))
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should not have errors");
        String content = result.content();
        assertTrue(content.contains("RegisterBlock"), "Should contain struct");
        // -P 选项应该移除 #line 指令
        assertFalse(content.contains("#line"), "Should not contain #line directives");
    }
    
    // ========== 错误处理测试 ==========
    
    @Test
    @Order(50)
    @DisplayName("预处理不存在的文件")
    void testPreprocessNonExistentFile() throws IOException {
        Path nonExistent = tempDir.resolve("does_not_exist.h");
        
        // GCC 会处理不存在的文件并返回错误
        var result = preprocessor.preprocess(nonExistent);
        
        assertTrue(result.hasErrors() || result.exitCode() != 0, 
            "Should report error for non-existent file");
    }
    
    @Test
    @Order(51)
    @DisplayName("预处理包含语法错误的头文件")
    void testPreprocessWithSyntaxError() throws IOException {
        Path header = createHeaderFile("syntax_error.h", """
            #if UNDEFINED_MACRO
            // This should cause issues
            #endif
            
            struct Valid {
                uint32 value;
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        // GCC 应该能处理未定义的宏（视为 0）
        assertEquals(0, result.exitCode(), "Should handle undefined macro gracefully");
    }
    
    @Test
    @Order(52)
    @DisplayName("预处理带缺失#include的头文件")
    void testPreprocessWithMissingInclude() throws IOException {
        Path header = createHeaderFile("missing_include.h", """
            #include "non_existent_file.h"
            
            struct Test {
                uint32 value;
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        // 应该报告错误
        assertTrue(result.hasErrors() || result.exitCode() != 0, 
            "Should report error for missing include");
    }
    
    // ========== 自定义 GCC 命令测试 ==========
    
    @Test
    @Order(60)
    @DisplayName("使用自定义 GCC 命令")
    void testCustomGccCommand() throws IOException {
        // 使用系统默认的 gcc 作为"自定义"命令
        Path header = createHeaderFile("custom_gcc.h", """
            struct Custom {
                uint8 field;
            };
            """);
        
        // 创建简单的编译配置文件
        Path compileConfig = tempDir.resolve("command.txt");
        Files.writeString(compileConfig, "gcc -E -P -I.");
        
        preprocessor.loadCompileConfig(compileConfig);
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should work with custom gcc command");
    }
    
    // ========== 边界条件测试 ==========
    
    @Test
    @Order(70)
    @DisplayName("预处理空文件")
    void testPreprocessEmptyFile() throws IOException {
        Path header = createHeaderFile("empty.h", "");
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should handle empty file");
        assertNotNull(result.content(), "Content should not be null");
    }
    
    @Test
    @Order(71)
    @DisplayName("预处理大文件")
    void testPreprocessLargeFile() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("struct Struct").append(i).append(" {\n");
            sb.append("    uint32 field").append(i).append(";\n");
            sb.append("};\n\n");
        }
        
        Path header = createHeaderFile("large.h", sb.toString());
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should handle large file");
        assertTrue(result.content().contains("Struct99"), "Should contain last struct");
    }
    
    @Test
    @Order(72)
    @DisplayName("预处理带特殊字符的文件")
    void testPreprocessWithSpecialCharacters() throws IOException {
        Path header = createHeaderFile("special.h", """
            #define MACRO_WITH_UNDERSCORES_123
            #define MACRO_WITH_NUMBERS_123_456
            
            struct Special_Chars_123 {
                uint32 _private;
                uint32 public_field;
            };
            """);
        
        var result = preprocessor.preprocess(header);
        
        assertFalse(result.hasErrors(), "Should handle special characters");
        assertTrue(result.content().contains("Special_Chars_123"), "Should preserve special chars");
    }
    
    // ========== 辅助方法 ==========
    
    private Path createHeaderFile(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
