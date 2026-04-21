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
        
        // 验证 ref_a 嵌套的 fields - 所有字段都要验证
        assertTrue(refA.has("fields"));
        JsonNode refAFields = refA.get("fields");
        assertEquals(2, refAFields.size());
        
        JsonNode refA_fieldA = refAFields.get(0);
        assertEquals("a", refA_fieldA.get("name").asText());
        assertEquals("uint7", refA_fieldA.get("type").asText());
        assertEquals(7, refA_fieldA.get("bits").asInt());
        assertEquals(0, refA_fieldA.get("offset").asInt());
        
        JsonNode refA_fieldB = refAFields.get(1);
        assertEquals("b", refA_fieldB.get("name").asText());
        assertEquals("uint1", refA_fieldB.get("type").asText());
        assertEquals(1, refA_fieldB.get("bits").asInt());
        assertEquals(7, refA_fieldB.get("offset").asInt());
        
        // 验证具名引用 ref_b
        JsonNode refB = structC.get("fields").get(1);
        assertEquals("ref_b", refB.get("name").asText());
        assertEquals("B", refB.get("type").asText());
        assertEquals(8, refB.get("bits").asInt());
        assertEquals(8, refB.get("offset").asInt());
        
        // 验证 ref_b 嵌套的 fields - 所有字段都要验证
        // offset 是相对于最外层的绝对偏移
        assertTrue(refB.has("fields"));
        JsonNode refBFields = refB.get("fields");
        assertEquals(2, refBFields.size());
        
        JsonNode refB_fieldC = refBFields.get(0);
        assertEquals("c", refB_fieldC.get("name").asText());
        assertEquals("uint4", refB_fieldC.get("type").asText());
        assertEquals(4, refB_fieldC.get("bits").asInt());
        assertEquals(8, refB_fieldC.get("offset").asInt());  // 相对于最外层：ref_b 的 offset(8) + c 在 B 中的 offset(0)
        
        JsonNode refB_fieldD = refBFields.get(1);
        assertEquals("d", refB_fieldD.get("name").asText());
        assertEquals("uint4", refB_fieldD.get("type").asText());
        assertEquals(4, refB_fieldD.get("bits").asInt());
        assertEquals(12, refB_fieldD.get("offset").asInt());  // 相对于最外层：ref_b 的 offset(8) + d 在 B 中的 offset(4)
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
        
        // 验证 g 的嵌套 fields - 所有字段都要验证
        assertTrue(fieldG.has("fields"));
        JsonNode gFields = fieldG.get("fields");
        assertEquals(2, gFields.size());
        assertFalse(gFields.get(0).has("fields")); // 普通字段没有嵌套
        assertFalse(gFields.get(1).has("fields"));
        
        JsonNode gFieldE = gFields.get(0);
        assertEquals("e", gFieldE.get("name").asText());
        assertEquals("uint10", gFieldE.get("type").asText());
        assertEquals(10, gFieldE.get("bits").asInt());
        assertEquals(0, gFieldE.get("offset").asInt());
        
        JsonNode gFieldF = gFields.get(1);
        assertEquals("f", gFieldF.get("name").asText());
        assertEquals("uint6", gFieldF.get("type").asText());
        assertEquals(6, gFieldF.get("bits").asInt());
        assertEquals(10, gFieldF.get("offset").asInt());
        
        // 验证匿名 union 嵌套 j
        JsonNode fieldJ = structD.get("fields").get(1);
        assertEquals("j", fieldJ.get("name").asText());
        assertEquals("", fieldJ.get("type").asText()); // 匿名
        assertEquals(16, fieldJ.get("bits").asInt());
        assertEquals(16, fieldJ.get("offset").asInt());
        
        // 验证 union 内的字段（offset 相对于最外层的绝对偏移）- 所有字段都要验证
        assertTrue(fieldJ.has("fields"));
        JsonNode jFields = fieldJ.get("fields");
        assertEquals(2, jFields.size());
        assertFalse(jFields.get(0).has("fields")); // 普通字段没有嵌套
        assertFalse(jFields.get(1).has("fields"));
        
        JsonNode jFieldH = jFields.get(0);
        assertEquals("h", jFieldH.get("name").asText());
        assertEquals("uint16", jFieldH.get("type").asText());
        assertEquals(16, jFieldH.get("bits").asInt());
        assertEquals(16, jFieldH.get("offset").asInt());  // Union 内字段 offset = union 的 offset
        
        JsonNode jFieldI = jFields.get(1);
        assertEquals("i", jFieldI.get("name").asText());
        assertEquals("uint16", jFieldI.get("type").asText());
        assertEquals(16, jFieldI.get("bits").asInt());
        assertEquals(16, jFieldI.get("offset").asInt());  // Union 内字段 offset = union 的 offset
        
        // 验证嵌套结构没有 anonymous 和 size_bits
        assertFalse(fieldG.has("anonymous"));
        assertFalse(fieldG.has("size_bits"));
        assertFalse(fieldJ.has("anonymous"));
        assertFalse(fieldJ.has("size_bits"));
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
