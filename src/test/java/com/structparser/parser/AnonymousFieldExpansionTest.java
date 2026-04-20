package com.structparser.parser;

import com.structparser.model.ParseResult;
import com.structparser.model.Struct;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 匿名 Union/Struct 字段展开测试
 */
public class AnonymousFieldExpansionTest {
    
    private final StructParserService parser = new StructParserService();
    
    @Test
    public void testAnonymousUnionFieldsExpanded() {
        String input = """
            struct Meta {
                union {
                    uint1 flag1;
                    uint1 flag2;
                };
                
                union {
                    uint1 flag3;
                    uint1 flag4;
                };
                
                uint22 typeid;
            };
            """;
        
        ParseResult result = parser.parse(input);
        
        assertFalse(result.hasErrors());
        assertEquals(1, result.structs().size());
        
        Struct meta = result.structs().get(0);
        assertEquals("Meta", meta.name());
        
        // 验证字段被展开，总共有 5 个字段（而不是 3 个嵌套字段）
        assertEquals(5, meta.fields().size());
        
        // 验证第一个匿名 union 的字段共享相同的 offset
        var flag1 = meta.fields().stream()
            .filter(f -> f.name().equals("flag1"))
            .findFirst();
        assertTrue(flag1.isPresent());
        assertEquals(0, flag1.get().bitOffset());
        assertEquals(1, flag1.get().bitWidth());
        
        var flag2 = meta.fields().stream()
            .filter(f -> f.name().equals("flag2"))
            .findFirst();
        assertTrue(flag2.isPresent());
        assertEquals(0, flag2.get().bitOffset());
        assertEquals(1, flag2.get().bitWidth());
        
        // 验证第二个匿名 union 的字段共享相同的 offset (1)
        var flag3 = meta.fields().stream()
            .filter(f -> f.name().equals("flag3"))
            .findFirst();
        assertTrue(flag3.isPresent());
        assertEquals(1, flag3.get().bitOffset());
        assertEquals(1, flag3.get().bitWidth());
        
        var flag4 = meta.fields().stream()
            .filter(f -> f.name().equals("flag4"))
            .findFirst();
        assertTrue(flag4.isPresent());
        assertEquals(1, flag4.get().bitOffset());
        assertEquals(1, flag4.get().bitWidth());
        
        // 验证 typeid 字段的 offset 是 2
        var typeid = meta.fields().stream()
            .filter(f -> f.name().equals("typeid"))
            .findFirst();
        assertTrue(typeid.isPresent());
        assertEquals(2, typeid.get().bitOffset());
        assertEquals(22, typeid.get().bitWidth());
        
        // 验证总位数
        // 第一个匿名 union (flag1, flag2) 占用 1 bit
        // 第二个匿名 union (flag3, flag4) 占用 1 bit
        // typeid 占用 22 bit
        // 总计: 1 + 1 + 22 = 24 bit
        assertEquals(24, meta.totalBits());
    }
    
    @Test
    public void testNamedUnionNotExpanded() {
        String input = """
            union MyUnion {
                uint8 a;
                uint8 b;
            };
            
            struct Container {
                union MyUnion data;
                uint8 c;
            };
            """;
        
        ParseResult result = parser.parse(input);
        
        assertFalse(result.hasErrors());
        assertEquals(1, result.structs().size());
        assertEquals(1, result.unions().size());
        
        Struct container = result.structs().get(0);
        // 命名 union 应该作为嵌套字段，不展开
        assertEquals(2, container.fields().size());
        
        var dataField = container.fields().stream()
            .filter(f -> f.name().equals("data"))
            .findFirst();
        assertTrue(dataField.isPresent());
        assertNotNull(dataField.get().nestedUnion());
    }
    
    @Test
    public void testAnonymousStructFieldsExpanded() {
        String input = """
            struct Outer {
                uint8 header;
                
                struct {
                    uint8 a;
                    uint8 b;
                };
                
                uint8 footer;
            };
            """;
        
        ParseResult result = parser.parse(input);
        
        assertFalse(result.hasErrors());
        assertEquals(1, result.structs().size());
        
        Struct outer = result.structs().get(0);
        // 匿名 struct 的字段应该被展开
        // header + a + b + footer = 4 个字段
        assertEquals(4, outer.fields().size());
        
        // 验证字段顺序和偏移量
        var fields = outer.fields();
        assertEquals("header", fields.get(0).name());
        assertEquals(0, fields.get(0).bitOffset());
        
        assertEquals("a", fields.get(1).name());
        assertEquals(8, fields.get(1).bitOffset());
        
        assertEquals("b", fields.get(2).name());
        assertEquals(16, fields.get(2).bitOffset());
        
        assertEquals("footer", fields.get(3).name());
        assertEquals(24, fields.get(3).bitOffset());
    }
}
