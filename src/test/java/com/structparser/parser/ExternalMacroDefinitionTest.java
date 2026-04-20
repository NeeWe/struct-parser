package com.structparser.parser;

import com.structparser.model.ParseResult;
import com.structparser.model.Struct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试通过外部头文件定义宏的场景
 * 使用 gcc -include 参数引用包含宏定义的头文件
 */
public class ExternalMacroDefinitionTest {
    
    @TempDir
    Path tempDir;
    
    private static final Path TEST_RESOURCES = Path.of("src/test/resources/headers");
    
    /**
     * 创建编译配置文件
     */
    private Path createCompileConfig(String gccCommand) throws IOException {
        Path configFile = tempDir.resolve("command.txt");
        Files.writeString(configFile, gccCommand);
        return configFile;
    }
    
    @Test
    @DisplayName("通过 -include 引用外部宏定义文件 - 全部特性启用")
    public void testExternalMacrosAllFeatures() throws IOException {
        // 复制 features.h 到临时目录
        Path featuresFile = tempDir.resolve("features.h");
        Files.copy(TEST_RESOURCES.resolve("features.h"), featuresFile);
        
        // 使用 -include 参数引用 features.h
        Path compileConfig = createCompileConfig(
            "gcc -E -P -I" + tempDir + " -include features.h"
        );
        
        var parser = new StructParserService();
        parser.loadCompileConfig(compileConfig);
        
        Path file = TEST_RESOURCES.resolve("config_with_external_macros.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        
        // 应该解析出 4 个结构体：FeatureAConfig, CacheConfig, DebugFeatureX, CommonSettings
        assertEquals(4, result.structs().size(), "Should find 4 structs");
        
        // FeatureAConfig 应该存在（因为 FEATURE_A 在 features.h 中定义）
        Struct featureA = result.getStructByName("FeatureAConfig");
        assertNotNull(featureA, "FeatureAConfig should exist when FEATURE_A is defined in external header");
        assertEquals(3, featureA.fields().size());
        
        // FeatureBConfig 不应该存在
        assertNull(result.getStructByName("FeatureBConfig"));
        
        // CacheConfig 应该存在（HIGH_PERF && ENABLE_CACHE）
        Struct cache = result.getStructByName("CacheConfig");
        assertNotNull(cache, "CacheConfig should exist with HIGH_PERF && ENABLE_CACHE");
        assertEquals(3, cache.fields().size());
        
        // PerformanceConfig 和 BasicConfig 不应该存在
        assertNull(result.getStructByName("PerformanceConfig"));
        assertNull(result.getStructByName("BasicConfig"));
        
        // DebugFeatureX 应该存在（FEATURE_X && DEBUG_MODE）
        Struct debug = result.getStructByName("DebugFeatureX");
        assertNotNull(debug, "DebugFeatureX should exist with FEATURE_X && DEBUG_MODE");
        assertEquals(2, debug.fields().size());
        
        // ReleaseFeatureX 不应该存在
        assertNull(result.getStructByName("ReleaseFeatureX"));
        
        // CommonSettings 总是存在
        Struct common = result.getStructByName("CommonSettings");
        assertNotNull(common);
    }
    
    @Test
    @DisplayName("通过 -include 引用部分宏定义 - 仅基础特性")
    public void testExternalMacrosPartialFeatures() throws IOException {
        // 创建一个只定义部分宏的文件
        Path partialMacros = tempDir.resolve("partial_features.h");
        Files.writeString(partialMacros, """
            #define FEATURE_A
            #define MEDIUM_PERF
            """);
        
        Path compileConfig = createCompileConfig(
            "gcc -E -P -I" + tempDir + " -include " + partialMacros
        );
        
        var parser = new StructParserService();
        parser.loadCompileConfig(compileConfig);
        
        Path file = TEST_RESOURCES.resolve("config_with_external_macros.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        
        // 应该解析出 3 个结构体：FeatureAConfig, PerformanceConfig, CommonSettings
        assertEquals(3, result.structs().size(), "Should find 3 structs");
        
        // FeatureAConfig 应该存在
        Struct featureA = result.getStructByName("FeatureAConfig");
        assertNotNull(featureA);
        
        // PerformanceConfig 应该存在（MEDIUM_PERF 满足条件）
        Struct perf = result.getStructByName("PerformanceConfig");
        assertNotNull(perf, "PerformanceConfig should exist with MEDIUM_PERF");
        assertEquals(2, perf.fields().size());
        
        // CacheConfig 不应该存在（没有 ENABLE_CACHE）
        assertNull(result.getStructByName("CacheConfig"));
        
        // DebugFeatureX 和 ReleaseFeatureX 都不应该存在（没有 FEATURE_X）
        assertNull(result.getStructByName("DebugFeatureX"));
        assertNull(result.getStructByName("ReleaseFeatureX"));
        
        // CommonSettings 总是存在
        Struct common = result.getStructByName("CommonSettings");
        assertNotNull(common);
    }
    
    @Test
    @DisplayName("不使用 -include - 所有条件都不满足")
    public void testNoExternalMacros() throws IOException {
        // 不使用 -include，没有任何宏定义
        Path compileConfig = createCompileConfig("gcc -E -P -I.");
        
        var parser = new StructParserService();
        parser.loadCompileConfig(compileConfig);
        
        Path file = TEST_RESOURCES.resolve("config_with_external_macros.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        
        // 应该解析出 3 个结构体：FeatureBConfig, BasicConfig, CommonSettings
        assertEquals(3, result.structs().size(), "Should find 3 structs");
        
        // FeatureBConfig 应该存在（else 分支）
        Struct featureB = result.getStructByName("FeatureBConfig");
        assertNotNull(featureB, "FeatureBConfig should exist when FEATURE_A is not defined");
        assertEquals(2, featureB.fields().size());
        
        // FeatureAConfig 不应该存在
        assertNull(result.getStructByName("FeatureAConfig"));
        
        // BasicConfig 应该存在（else 分支）
        Struct basic = result.getStructByName("BasicConfig");
        assertNotNull(basic, "BasicConfig should exist in default branch");
        assertEquals(2, basic.fields().size());
        
        // CacheConfig 和 PerformanceConfig 都不应该存在
        assertNull(result.getStructByName("CacheConfig"));
        assertNull(result.getStructByName("PerformanceConfig"));
        
        // DebugFeatureX 和 ReleaseFeatureX 都不应该存在
        assertNull(result.getStructByName("DebugFeatureX"));
        assertNull(result.getStructByName("ReleaseFeatureX"));
        
        // CommonSettings 总是存在
        Struct common = result.getStructByName("CommonSettings");
        assertNotNull(common);
    }
    
    @Test
    @DisplayName("混合使用 -D 和 -include")
    public void testMixedDAndInclude() throws IOException {
        // 创建一个定义部分宏的文件
        Path baseMacros = tempDir.resolve("base_features.h");
        Files.writeString(baseMacros, """
            #define HIGH_PERF
            """);
        
        // 使用 -include 引用基础宏，同时用 -D 添加额外的宏
        Path compileConfig = createCompileConfig(
            "gcc -E -P -I" + tempDir + " -include base_features.h -DENABLE_CACHE"
        );
        
        var parser = new StructParserService();
        parser.loadCompileConfig(compileConfig);
        
        Path file = TEST_RESOURCES.resolve("config_with_external_macros.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        
        // 应该解析出 3 个结构体：FeatureBConfig, CacheConfig, CommonSettings
        assertEquals(3, result.structs().size(), "Should find 3 structs");
        
        // FeatureBConfig 应该存在（没有 FEATURE_A）
        Struct featureB = result.getStructByName("FeatureBConfig");
        assertNotNull(featureB);
        
        // CacheConfig 应该存在（HIGH_PERF from -include, ENABLE_CACHE from -D）
        Struct cache = result.getStructByName("CacheConfig");
        assertNotNull(cache, "CacheConfig should exist with HIGH_PERF (from -include) && ENABLE_CACHE (from -D)");
        assertEquals(3, cache.fields().size());
        
        // PerformanceConfig 不应该存在（因为 CacheConfig 的优先级更高）
        assertNull(result.getStructByName("PerformanceConfig"));
        
        // CommonSettings 总是存在
        Struct common = result.getStructByName("CommonSettings");
        assertNotNull(common);
    }
    
    @Test
    @DisplayName("使用 -imacros 引用宏定义文件")
    public void testImacrosOption() throws IOException {
        // 复制 features.h 到临时目录
        Path featuresFile = tempDir.resolve("features.h");
        Files.copy(TEST_RESOURCES.resolve("features.h"), featuresFile);
        
        // 使用 -imacros 参数引用 features.h
        // -imacros 与 -include 类似，但宏定义不会出现在预处理输出中
        Path compileConfig = createCompileConfig(
            "gcc -E -P -I" + tempDir + " -imacros features.h"
        );
        
        var parser = new StructParserService();
        parser.loadCompileConfig(compileConfig);
        
        Path file = TEST_RESOURCES.resolve("config_with_external_macros.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        
        // 应该解析出 4 个结构体：FeatureAConfig, CacheConfig, DebugFeatureX, CommonSettings
        assertEquals(4, result.structs().size(), "Should find 4 structs");
        
        // FeatureAConfig 应该存在
        Struct featureA = result.getStructByName("FeatureAConfig");
        assertNotNull(featureA);
        
        // CacheConfig 应该存在
        Struct cache = result.getStructByName("CacheConfig");
        assertNotNull(cache);
        
        // DebugFeatureX 应该存在
        Struct debug = result.getStructByName("DebugFeatureX");
        assertNotNull(debug);
        
        // CommonSettings 总是存在
        Struct common = result.getStructByName("CommonSettings");
        assertNotNull(common);
    }
}
