package com.structparser.parser;

import com.structparser.model.ParseResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证交叉引用检测功能
 */
public class CircularReferenceTest {
    
    @Test
    public void testCircularStructReference() {
        var parser = new StructParserService();
        
        // NodeA 引用 NodeB，NodeB 引用 NodeA - 这是交叉引用
        String input = """
            struct NodeA {
                uint8 value;
                NodeB next;
            };
            
            struct NodeB {
                uint16 data;
                NodeA prev;
            };
            """;
        
        ParseResult result = parser.parse(input);
        
        // 应该检测到交叉引用或前向引用错误
        assertTrue(result.hasErrors(), "Should detect circular/forward reference");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Circular reference") || e.contains("Forward reference")), 
            "Error should mention circular or forward reference: " + result.errors());
    }
    
    @Test
    public void testCircularUnionReference() {
        var parser = new StructParserService();
        
        // UnionA 引用 UnionB，UnionB 引用 UnionA - 这是交叉引用
        String input = """
            union UnionA {
                uint32 raw;
                UnionB nested;
            };
            
            union UnionB {
                uint16 value;
                UnionA ref;
            };
            """;
        
        ParseResult result = parser.parse(input);
        
        // 应该检测到交叉引用或前向引用错误
        assertTrue(result.hasErrors(), "Should detect circular/forward reference");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Circular reference") || e.contains("Forward reference")), 
            "Error should mention circular or forward reference: " + result.errors());
    }
    
    @Test
    public void testSelfReference() {
        var parser = new StructParserService();
        
        // 结构体引用自身 - 这也是交叉引用
        String input = """
            struct Node {
                uint8 value;
                Node next;
            };
            """;
        
        ParseResult result = parser.parse(input);
        
        // 应该检测到交叉引用错误
        assertTrue(result.hasErrors(), "Should detect self-reference");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Circular reference")), 
            "Error should mention circular reference: " + result.errors());
    }
    
    @Test
    public void testValidNestedReference() {
        var parser = new StructParserService();
        
        // 正常的嵌套引用（非交叉引用）应该成功
        String input = """
            struct Inner {
                uint8 a;
                uint8 b;
            };
            
            struct Outer {
                uint8 header;
                Inner inner;
                uint8 footer;
            };
            """;
        
        ParseResult result = parser.parse(input);
        
        // 不应该有错误
        assertFalse(result.hasErrors(), "Valid nested reference should not cause errors: " + result.errors());
        assertEquals(2, result.structs().size());
        
        // 验证嵌套结构正确解析
        var outer = result.getStructByName("Outer");
        assertNotNull(outer);
        assertEquals(3, outer.fields().size()); // header, inner, footer
        
        var innerField = outer.fields().get(1);
        assertEquals("inner", innerField.name());
        assertNotNull(innerField.nestedStruct());
    }
    
    @Test
    public void testThreeWayCircularReference() {
        var parser = new StructParserService();
        
        // A -> B -> C -> A 的三方循环引用
        String input = """
            struct A {
                uint8 val;
                B ref_b;
            };
            
            struct B {
                uint16 val;
                C ref_c;
            };
            
            struct C {
                uint32 val;
                A ref_a;
            };
            """;
        
        ParseResult result = parser.parse(input);
        
        // 应该检测到交叉引用或前向引用错误
        assertTrue(result.hasErrors(), "Should detect circular/forward reference in three-way cycle");
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Circular reference") || e.contains("Forward reference")), 
            "Error should mention circular or forward reference: " + result.errors());
    }
}
