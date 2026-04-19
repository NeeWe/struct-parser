package com.structparser.parser;

import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证顶层 Union 和嵌套 Union 的 offset 行为
 */
public class TopLevelVsNestedUnionTest {
    
    @Test
    public void testTopLevelUnion() {
        var parser = new StructParserService();
        String input = """
            union Data {
                uint32 word;
                uint8 bytes;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        assertEquals(1, result.unions().size());
        
        var union = result.unions().get(0);
        assertEquals("Data", union.name());
        
        // 顶层 union 的字段 offset 应该都是 0
        union.fields().forEach(f -> {
            System.out.println("Field: " + f.name() + ", offset: " + f.bitOffset());
            assertEquals(0, f.bitOffset(), "Top-level union field should have offset 0");
        });
    }
    
    @Test
    public void testNestedUnion() {
        var parser = new StructParserService();
        String input = """
            struct Container {
                uint8 type;
                union {
                    uint16 word;
                    uint8 bytes;
                } data;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        assertEquals(1, result.structs().size());
        
        var struct = result.structs().get(0);
        var dataField = struct.fields().stream()
            .filter(f -> f.name().equals("data"))
            .findFirst();
        
        assertTrue(dataField.isPresent());
        assertNotNull(dataField.get().nestedUnion());
        
        var union = dataField.get().nestedUnion();
        System.out.println("\nNested union fields:");
        
        // 嵌套 union 的字段应该有绝对偏移量（等于 union 字段的 offset）
        union.fields().forEach(f -> {
            System.out.println("Field: " + f.name() + ", offset: " + f.bitOffset());
            assertEquals(8, f.bitOffset(), "Nested union field should have absolute offset 8");
        });
    }
}
