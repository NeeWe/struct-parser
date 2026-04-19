package com.structparser.parser;

import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 基于 example.h 的集成测试
 * 验证关键的 JSON 输出格式
 */
public class ExampleIntegrationTest {
    
    @Test
    public void testStructA() {
        var parser = new StructParserService();
        String input = """
            struct A {
                uint7 a;
                uint1 b;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        assertEquals(1, result.structs().size());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 验证顶层结构
        assertTrue(json.contains("\"name\" : \"\""));
        assertTrue(json.contains("\"type\" : \"A\""));
        assertTrue(json.contains("\"bits\" : 8"));
        
        // 验证字段
        assertTrue(json.contains("\"name\" : \"a\""));
        assertTrue(json.contains("\"type\" : \"uint7\""));
        assertTrue(json.contains("\"bits\" : 7"));
        assertTrue(json.contains("\"offset\" : 0"));
        
        assertTrue(json.contains("\"name\" : \"b\""));
        assertTrue(json.contains("\"type\" : \"uint1\""));
        assertTrue(json.contains("\"bits\" : 1"));
        assertTrue(json.contains("\"offset\" : 7"));
        
        // 不应该有 anonymous 和 size_bits
        assertFalse(json.contains("\"anonymous\""));
        assertFalse(json.contains("\"size_bits\""));
    }
    
    @Test
    public void testStructB() {
        var parser = new StructParserService();
        String input = """
            struct B {
                uint4 c;
                uint4 d;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        assertEquals(1, result.structs().size());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 验证顶层结构
        assertTrue(json.contains("\"name\" : \"\""));
        assertTrue(json.contains("\"type\" : \"B\""));
        assertTrue(json.contains("\"bits\" : 8"));
        
        // 验证字段
        assertTrue(json.contains("\"name\" : \"c\""));
        assertTrue(json.contains("\"type\" : \"uint4\""));
        assertTrue(json.contains("\"bits\" : 4"));
        assertTrue(json.contains("\"offset\" : 0"));
        
        assertTrue(json.contains("\"name\" : \"d\""));
        assertTrue(json.contains("\"type\" : \"uint4\""));
        assertTrue(json.contains("\"bits\" : 4"));
        assertTrue(json.contains("\"offset\" : 4"));
    }
    
    @Test
    public void testStructC_NamedReferences() {
        var parser = new StructParserService();
        String input = """
            struct A {
                uint7 a;
                uint1 b;
            };
            
            struct B {
                uint4 c;
                uint4 d;
            };
            
            struct C {
                struct A ref_a;
                struct B ref_b;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        assertEquals(3, result.structs().size());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 验证 struct C
        assertTrue(json.contains("\"type\" : \"C\""));
        assertTrue(json.contains("\"bits\" : 16"));
        
        // 验证具名引用 ref_a
        assertTrue(json.contains("\"name\" : \"ref_a\""));
        assertTrue(json.contains("\"type\" : \"A\""));
        assertTrue(json.contains("\"bits\" : 8"));
        assertTrue(json.contains("\"offset\" : 0"));
        // 验证嵌套的 fields
        assertTrue(json.contains("\"name\" : \"ref_a\""));
        
        // 验证具名引用 ref_b
        assertTrue(json.contains("\"name\" : \"ref_b\""));
        assertTrue(json.contains("\"type\" : \"B\""));
        assertTrue(json.contains("\"bits\" : 8"));
        assertTrue(json.contains("\"offset\" : 8"));
    }
    
    @Test
    public void testStructD_AnonymousNested() {
        var parser = new StructParserService();
        String input = """
            struct D {
                struct {
                    uint10 e;
                    uint6 f;
                } g;
                union {
                    uint16 h;
                    uint16 i;
                } j;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        assertEquals(1, result.structs().size());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 验证顶层结构
        assertTrue(json.contains("\"type\" : \"D\""));
        assertTrue(json.contains("\"bits\" : 32"));
        
        // 验证匿名 struct 嵌套 g
        assertTrue(json.contains("\"name\" : \"g\""));
        assertTrue(json.contains("\"type\" : \"\"")); // 匿名
        assertTrue(json.contains("\"bits\" : 16"));
        assertTrue(json.contains("\"offset\" : 0"));
        
        // 验证 g 的嵌套 fields
        assertTrue(json.contains("\"name\" : \"e\""));
        assertTrue(json.contains("\"type\" : \"uint10\""));
        assertTrue(json.contains("\"bits\" : 10"));
        assertTrue(json.contains("\"offset\" : 0"));
        
        assertTrue(json.contains("\"name\" : \"f\""));
        assertTrue(json.contains("\"type\" : \"uint6\""));
        assertTrue(json.contains("\"bits\" : 6"));
        assertTrue(json.contains("\"offset\" : 10"));
        
        // 验证匿名 union 嵌套 j
        assertTrue(json.contains("\"name\" : \"j\""));
        assertTrue(json.contains("\"type\" : \"\"")); // 匿名
        assertTrue(json.contains("\"bits\" : 16"));
        assertTrue(json.contains("\"offset\" : 16"));
        
        // 验证 union 内的字段（offset 相对于 union，都是 0）
        assertTrue(json.contains("\"name\" : \"h\""));
        assertTrue(json.contains("\"type\" : \"uint16\""));
        assertTrue(json.contains("\"bits\" : 16"));
        
        assertTrue(json.contains("\"name\" : \"i\""));
        assertTrue(json.contains("\"type\" : \"uint16\""));
        assertTrue(json.contains("\"bits\" : 16"));
    }
    
    @Test
    public void testCompleteExample_OutputFormat() {
        var parser = new StructParserService();
        String input = """
            struct A {
                uint7 a;
                uint1 b;
            };
            
            struct B {
                uint4 c;
                uint4 d;
            };
            
            struct C {
                struct A ref_a;
                struct B ref_b;
            };
            
            struct D {
                struct {
                    uint10 e;
                    uint6 f;
                } g;
                union {
                    uint16 h;
                    uint16 i;
                } j;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        assertEquals(4, result.structs().size()); // A, B, C, D
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 验证所有顶层结构的 name 都是空字符串
        long nameCount = json.split("\"name\" : \"\"").length - 1;
        assertTrue(nameCount >= 4, "Should have at least 4 top-level structures with empty name");
        
        // 验证没有 anonymous 和 size_bits 字段
        assertFalse(json.contains("\"anonymous\" :"));
        assertFalse(json.contains("\"size_bits\""));
        
        // 验证统一的 bits 字段
        long bitsCount = json.split("\"bits\" :").length - 1;
        assertTrue(bitsCount > 10, "Should have many bits fields");
        
        System.out.println("Complete Example JSON:");
        System.out.println(json);
    }
}
