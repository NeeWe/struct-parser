package com.structparser;

import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import com.structparser.parser.StructParserService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证具名引用的 type 字段使用名称而不是 "struct"/"union"
 */
public class NamedRefTypeTest {
    
    @Test
    public void testNamedStructRefType() {
        var parser = new StructParserService();
        
        String input = """
            struct Point {
                uint16 x;
                uint16 y;
            };
            
            struct Rectangle {
                struct Point topLeft;
                struct Point bottomRight;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        System.out.println("Named Struct Ref JSON:");
        System.out.println(json);
        
        // 验证具名引用使用 "Point" 而不是 "struct"
        assertTrue(json.contains("\"type\" : \"Point\""));
        // 不应该有 "type" : "struct" 在嵌套字段中（只有顶层的 Point 定义有）
        String[] lines = json.split("\n");
        boolean inNestedField = false;
        for (String line : lines) {
            if (line.contains("\"name\" : \"topLeft\"") || line.contains("\"name\" : \"bottomRight\"")) {
                inNestedField = true;
            }
            if (inNestedField && line.contains("\"type\"")) {
                assertTrue(line.contains("\"Point\""), 
                    "Named reference should use 'Point' as type, not 'struct': " + line);
                inNestedField = false;
            }
        }
    }
    
    @Test
    public void testAnonymousVsNamedType() {
        var parser = new StructParserService();
        
        String input = """
            struct Inner {
                uint8 a;
                uint8 b;
            };
            
            struct Outer {
                // 具名引用
                struct Inner named;
                // 匿名嵌套
                struct {
                    uint8 x;
                    uint8 y;
                } anonymous;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        System.out.println("Anonymous vs Named JSON:");
        System.out.println(json);
        
        // 具名引用应该使用 "Inner"
        assertTrue(json.contains("\"type\" : \"Inner\""));
        // 匿名嵌套应该使用空字符串
        assertTrue(json.contains("\"type\" : \"\""));
        // 不应该有 anonymous 属性（检查键，不是值）
        assertFalse(json.contains("\"anonymous\" :"));
    }
    
    @Test
    public void testNamedUnionRefType() {
        var parser = new StructParserService();
        
        String input = """
            union DataValue {
                uint32 raw;
                uint16 half;
            };
            
            struct Container {
                union DataValue data;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        System.out.println("Named Union Ref JSON:");
        System.out.println(json);
        
        // 具名引用应该使用 "DataValue" 而不是 "union"
        assertTrue(json.contains("\"type\" : \"DataValue\""));
    }
}
