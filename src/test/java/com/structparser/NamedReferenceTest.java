package com.structparser;

import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import com.structparser.parser.StructParserService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证具名 struct/union 引用的嵌套结构输出
 */
public class NamedReferenceTest {
    
    @Test
    public void testNamedStructReference() {
        var parser = new StructParserService();
        
        String input = """
            struct Inner {
                uint8 a;
                uint8 b;
            };
            
            struct Outer {
                uint8 header;
                struct Inner inner;
                uint8 footer;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors(), "Errors: " + result.errors());
        assertEquals(2, result.structs().size());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 验证 JSON 包含嵌套的 fields
        assertTrue(json.contains("\"name\" : \"inner\""));
        assertTrue(json.contains("\"type\" : \"struct\""));
        assertTrue(json.contains("\"fields\" : ["));
        assertTrue(json.contains("\"name\" : \"a\""));
        assertTrue(json.contains("\"name\" : \"b\""));
        
        System.out.println("Named Struct Reference JSON:");
        System.out.println(json);
    }
    
    @Test
    public void testNamedUnionReference() {
        var parser = new StructParserService();
        
        String input = """
            union DataUnion {
                uint32 raw;
                struct {
                    uint16 low;
                    uint16 high;
                } words;
            };
            
            struct Container {
                uint8 type;
                union DataUnion data;
                uint8 valid;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors(), "Errors: " + result.errors());
        assertEquals(1, result.structs().size());
        assertEquals(1, result.unions().size());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 验证 JSON 包含嵌套的 union fields
        assertTrue(json.contains("\"name\" : \"data\""));
        assertTrue(json.contains("\"type\" : \"union\""));
        assertTrue(json.contains("\"name\" : \"raw\""));
        assertTrue(json.contains("\"name\" : \"words\""));
        
        System.out.println("Named Union Reference JSON:");
        System.out.println(json);
    }
    
    @Test
    public void testMultipleReferences() {
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
        assertFalse(result.hasErrors(), "Errors: " + result.errors());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        
        // 验证两个 Point 引用都包含嵌套 fields
        long pointCount = json.split("\"name\" : \"topLeft\"").length - 1;
        assertEquals(1, pointCount);
        
        long fieldsCount = json.split("\"fields\" : \\[").length - 1;
        assertTrue(fieldsCount >= 3); // Point + Rectangle.topLeft + Rectangle.bottomRight
        
        System.out.println("Multiple References JSON:");
        System.out.println(json);
    }
}
