package com.structparser.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 基于 example.h 的集成测试
 * 验证关键的 JSON 输出格式
 */
public class ExampleIntegrationTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    public void testStructA() throws Exception {
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
        JsonNode root = objectMapper.readTree(json);
        
        // 验证顶层结构
        JsonNode structA = root.get("structs").get(0);
        assertEquals("", structA.get("name").asText());
        assertEquals("A", structA.get("type").asText());
        assertEquals(8, structA.get("bits").asInt());
        
        // 验证字段
        JsonNode fields = structA.get("fields");
        assertEquals(2, fields.size());
        
        JsonNode fieldA = fields.get(0);
        assertEquals("a", fieldA.get("name").asText());
        assertEquals("uint7", fieldA.get("type").asText());
        assertEquals(7, fieldA.get("bits").asInt());
        assertEquals(0, fieldA.get("offset").asInt());
        
        JsonNode fieldB = fields.get(1);
        assertEquals("b", fieldB.get("name").asText());
        assertEquals("uint1", fieldB.get("type").asText());
        assertEquals(1, fieldB.get("bits").asInt());
        assertEquals(7, fieldB.get("offset").asInt());
        
        // 不应该有 anonymous 和 size_bits
        assertFalse(structA.has("anonymous"));
        assertFalse(structA.has("size_bits"));
    }
    
    @Test
    public void testStructB() throws Exception {
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
        JsonNode root = objectMapper.readTree(json);
        
        // 验证顶层结构
        JsonNode structB = root.get("structs").get(0);
        assertEquals("", structB.get("name").asText());
        assertEquals("B", structB.get("type").asText());
        assertEquals(8, structB.get("bits").asInt());
        
        // 验证字段
        JsonNode fields = structB.get("fields");
        assertEquals(2, fields.size());
        
        JsonNode fieldC = fields.get(0);
        assertEquals("c", fieldC.get("name").asText());
        assertEquals("uint4", fieldC.get("type").asText());
        assertEquals(4, fieldC.get("bits").asInt());
        assertEquals(0, fieldC.get("offset").asInt());
        
        JsonNode fieldD = fields.get(1);
        assertEquals("d", fieldD.get("name").asText());
        assertEquals("uint4", fieldD.get("type").asText());
        assertEquals(4, fieldD.get("bits").asInt());
        assertEquals(4, fieldD.get("offset").asInt());
    }
    
    @Test
    public void testStructC_NamedReferences() throws Exception {
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
        JsonNode root = objectMapper.readTree(json);
        
        // 验证 struct C (第三个)
        JsonNode structC = root.get("structs").get(2);
        assertEquals("", structC.get("name").asText());
        assertEquals("C", structC.get("type").asText());
        assertEquals(16, structC.get("bits").asInt());
        
        // 验证具名引用 ref_a
        JsonNode refA = structC.get("fields").get(0);
        assertEquals("ref_a", refA.get("name").asText());
        assertEquals("A", refA.get("type").asText());
        assertEquals(8, refA.get("bits").asInt());
        assertEquals(0, refA.get("offset").asInt());
        // 验证嵌套的 fields
        assertTrue(refA.has("fields"));
        assertEquals(2, refA.get("fields").size());
        
        // 验证具名引用 ref_b
        JsonNode refB = structC.get("fields").get(1);
        assertEquals("ref_b", refB.get("name").asText());
        assertEquals("B", refB.get("type").asText());
        assertEquals(8, refB.get("bits").asInt());
        assertEquals(8, refB.get("offset").asInt());
        // 验证嵌套的 fields
        assertTrue(refB.has("fields"));
        assertEquals(2, refB.get("fields").size());
    }
    
    @Test
    public void testStructD_AnonymousNested() throws Exception {
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
        JsonNode root = objectMapper.readTree(json);
        
        // 验证顶层结构
        JsonNode structD = root.get("structs").get(0);
        assertEquals("", structD.get("name").asText());
        assertEquals("D", structD.get("type").asText());
        assertEquals(32, structD.get("bits").asInt());
        
        // 验证匿名 struct 嵌套 g
        JsonNode fieldG = structD.get("fields").get(0);
        assertEquals("g", fieldG.get("name").asText());
        assertEquals("", fieldG.get("type").asText()); // 匿名
        assertEquals(16, fieldG.get("bits").asInt());
        assertEquals(0, fieldG.get("offset").asInt());
        
        // 验证 g 的嵌套 fields
        assertTrue(fieldG.has("fields"));
        JsonNode gFields = fieldG.get("fields");
        assertEquals(2, gFields.size());
        
        assertEquals("e", gFields.get(0).get("name").asText());
        assertEquals("uint10", gFields.get(0).get("type").asText());
        assertEquals(10, gFields.get(0).get("bits").asInt());
        assertEquals(0, gFields.get(0).get("offset").asInt());
        
        assertEquals("f", gFields.get(1).get("name").asText());
        assertEquals("uint6", gFields.get(1).get("type").asText());
        assertEquals(6, gFields.get(1).get("bits").asInt());
        assertEquals(10, gFields.get(1).get("offset").asInt());
        
        // 验证匿名 union 嵌套 j
        JsonNode fieldJ = structD.get("fields").get(1);
        assertEquals("j", fieldJ.get("name").asText());
        assertEquals("", fieldJ.get("type").asText()); // 匿名
        assertEquals(16, fieldJ.get("bits").asInt());
        assertEquals(16, fieldJ.get("offset").asInt());
        
        // 验证 union 内的字段（offset 相对于 union，都是 0）
        assertTrue(fieldJ.has("fields"));
        JsonNode jFields = fieldJ.get("fields");
        assertEquals(2, jFields.size());
        
        assertEquals("h", jFields.get(0).get("name").asText());
        assertEquals("uint16", jFields.get(0).get("type").asText());
        assertEquals(16, jFields.get(0).get("bits").asInt());
        assertEquals(0, jFields.get(0).get("offset").asInt());
        
        assertEquals("i", jFields.get(1).get("name").asText());
        assertEquals("uint16", jFields.get(1).get("type").asText());
        assertEquals(16, jFields.get(1).get("bits").asInt());
        assertEquals(0, jFields.get(1).get("offset").asInt());
    }
    
    @Test
    public void testCompleteExample_OutputFormat() throws Exception {
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
        JsonNode root = objectMapper.readTree(json);
        
        // 验证所有顶层结构的 name 都是空字符串
        JsonNode structs = root.get("structs");
        assertEquals(4, structs.size());
        for (int i = 0; i < structs.size(); i++) {
            assertEquals("", structs.get(i).get("name").asText(), 
                "Struct at index " + i + " should have empty name");
        }
        
        // 验证没有 anonymous 和 size_bits 字段
        for (int i = 0; i < structs.size(); i++) {
            JsonNode struct = structs.get(i);
            assertFalse(struct.has("anonymous"), 
                "Struct " + struct.get("type").asText() + " should not have anonymous field");
            assertFalse(struct.has("size_bits"), 
                "Struct " + struct.get("type").asText() + " should not have size_bits field");
            assertTrue(struct.has("bits"), 
                "Struct " + struct.get("type").asText() + " should have bits field");
        }
        
        // 打印完整 JSON 用于调试
        System.out.println("Complete Example JSON:");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }
}
