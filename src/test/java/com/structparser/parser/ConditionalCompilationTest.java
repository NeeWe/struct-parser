package com.structparser.parser;

import com.structparser.model.ParseResult;
import com.structparser.model.Struct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试条件宏编译场景
 * 注意：这些测试使用的是已经过 GCC 预处理后的文件，模拟不同编译条件下的输出
 */
public class ConditionalCompilationTest {
    
    private static final Path TEST_RESOURCES = Paths.get("src/test/resources/headers");
    
    @Test
    @DisplayName("简单条件宏 - FEATURE_A 定义时")
    public void testSimpleConditionalWithFeatureA() throws IOException {
        var parser = new StructParserService()
            .disableGccPreprocessing();
        
        Path file = TEST_RESOURCES.resolve("conditional_simple_feature_a.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertEquals(2, result.structs().size(), "Should find 2 structs");
        
        // FeatureAConfig 应该存在
        Struct featureA = result.getStructByName("FeatureAConfig");
        assertNotNull(featureA, "FeatureAConfig should exist when FEATURE_A is defined");
        assertEquals(3, featureA.fields().size());
        assertEquals(32, featureA.totalBits());
        
        // FeatureBConfig 不应该存在
        assertNull(result.getStructByName("FeatureBConfig"), 
            "FeatureBConfig should not exist when FEATURE_A is defined");
        
        // CommonConfig 总是存在
        Struct common = result.getStructByName("CommonConfig");
        assertNotNull(common, "CommonConfig should always exist");
        assertEquals(2, common.fields().size());
    }
    
    @Test
    @DisplayName("简单条件宏 - FEATURE_A 未定义时")
    public void testSimpleConditionalWithoutFeatureA() throws IOException {
        var parser = new StructParserService()
            .disableGccPreprocessing();
        
        Path file = TEST_RESOURCES.resolve("conditional_simple_feature_b.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertEquals(2, result.structs().size(), "Should find 2 structs");
        
        // FeatureAConfig 不应该存在
        assertNull(result.getStructByName("FeatureAConfig"), 
            "FeatureAConfig should not exist when FEATURE_A is not defined");
        
        // FeatureBConfig 应该存在
        Struct featureB = result.getStructByName("FeatureBConfig");
        assertNotNull(featureB, "FeatureBConfig should exist when FEATURE_A is not defined");
        assertEquals(2, featureB.fields().size());
        assertEquals(32, featureB.totalBits());
        
        // CommonConfig 总是存在
        Struct common = result.getStructByName("CommonConfig");
        assertNotNull(common, "CommonConfig should always exist");
    }
    
    @Test
    @DisplayName("复杂条件宏 - HIGH_PERF && ENABLE_CACHE")
    public void testComplexConditionalCacheMode() throws IOException {
        var parser = new StructParserService()
            .disableGccPreprocessing();
        
        Path file = TEST_RESOURCES.resolve("conditional_complex_cache.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertEquals(2, result.structs().size(), "Should find 2 structs");
        
        // CacheConfig 应该在 HIGH_PERF && ENABLE_CACHE 时存在
        Struct cache = result.getStructByName("CacheConfig");
        assertNotNull(cache, "CacheConfig should exist with HIGH_PERF && ENABLE_CACHE");
        assertEquals(3, cache.fields().size());
        assertEquals(32, cache.totalBits());
        
        // PerformanceConfig 和 BasicConfig 不应该存在
        assertNull(result.getStructByName("PerformanceConfig"));
        assertNull(result.getStructByName("BasicConfig"));
        
        // GlobalSettings 总是存在
        Struct global = result.getStructByName("GlobalSettings");
        assertNotNull(global);
    }
    
    @Test
    @DisplayName("复杂条件宏 - HIGH_PERF || MEDIUM_PERF")
    public void testComplexConditionalPerfMode() throws IOException {
        var parser = new StructParserService()
            .disableGccPreprocessing();
        
        Path file = TEST_RESOURCES.resolve("conditional_complex_perf.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertEquals(2, result.structs().size(), "Should find 2 structs");
        
        // PerformanceConfig 应该在 HIGH_PERF || MEDIUM_PERF 时存在（但不满足 CACHE）
        Struct perf = result.getStructByName("PerformanceConfig");
        assertNotNull(perf, "PerformanceConfig should exist with HIGH_PERF || MEDIUM_PERF");
        assertEquals(2, perf.fields().size());
        assertEquals(32, perf.totalBits());
        
        // CacheConfig 不应该存在（因为 ENABLE_CACHE 未定义）
        assertNull(result.getStructByName("CacheConfig"));
        assertNull(result.getStructByName("BasicConfig"));
        
        // GlobalSettings 总是存在
        Struct global = result.getStructByName("GlobalSettings");
        assertNotNull(global);
    }
    
    @Test
    @DisplayName("嵌套条件宏 - FEATURE_X && DEBUG_MODE")
    public void testNestedConditionalDebugMode() throws IOException {
        var parser = new StructParserService()
            .disableGccPreprocessing();
        
        Path file = TEST_RESOURCES.resolve("conditional_complex_debug.h");
        ParseResult result = parser.parseFile(file);
        
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertEquals(3, result.structs().size(), "Should find 3 structs");
        
        // BasicConfig 在 else 分支，应该存在
        Struct basic = result.getStructByName("BasicConfig");
        assertNotNull(basic, "BasicConfig should exist in else branch");
        
        // DebugFeatureX 应该在 FEATURE_X && DEBUG_MODE 时存在
        Struct debug = result.getStructByName("DebugFeatureX");
        assertNotNull(debug, "DebugFeatureX should exist with FEATURE_X && DEBUG_MODE");
        assertEquals(2, debug.fields().size());
        assertEquals(48, debug.totalBits());
        
        // ReleaseFeatureX 不应该存在
        assertNull(result.getStructByName("ReleaseFeatureX"),
            "ReleaseFeatureX should not exist in DEBUG_MODE");
        
        // GlobalSettings 总是存在
        Struct global = result.getStructByName("GlobalSettings");
        assertNotNull(global);
    }
}
