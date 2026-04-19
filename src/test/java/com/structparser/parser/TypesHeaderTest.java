package com.structparser.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 types.h 文件的解析结果
 */
public class TypesHeaderTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    public void testTypesHeaderParsing() throws IOException {
        var parser = new StructParserService();
        
        // 读取 types.h 文件内容
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
              A ref_a;
              B ref_b;
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
        
        // 验证没有错误
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertEquals(4, result.structs().size(), "Should have 4 structs");
        
        // 生成 JSON
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        JsonNode root = objectMapper.readTree(json);
        
        // 验证结构体数量
        JsonNode structs = root.get("structs");
        assertEquals(4, structs.size());
        
        // ==================== 验证结构体 A ====================
        JsonNode structA = findStructByType(structs, "A");
        assertNotNull(structA, "Struct A should exist");
        assertEquals("", structA.get("name").asText());
        assertEquals("A", structA.get("type").asText());
        assertEquals(8, structA.get("bits").asInt());
        
        JsonNode fieldsA = structA.get("fields");
        assertEquals(2, fieldsA.size());
        
        // 字段 a: uint7, offset 0
        assertEquals("a", fieldsA.get(0).get("name").asText());
        assertEquals("uint7", fieldsA.get(0).get("type").asText());
        assertEquals(7, fieldsA.get(0).get("bits").asInt());
        assertEquals(0, fieldsA.get(0).get("offset").asInt());
        
        // 字段 b: uint1, offset 7
        assertEquals("b", fieldsA.get(1).get("name").asText());
        assertEquals("uint1", fieldsA.get(1).get("type").asText());
        assertEquals(1, fieldsA.get(1).get("bits").asInt());
        assertEquals(7, fieldsA.get(1).get("offset").asInt());
        
        // ==================== 验证结构体 B ====================
        JsonNode structB = findStructByType(structs, "B");
        assertNotNull(structB, "Struct B should exist");
        assertEquals("", structB.get("name").asText());
        assertEquals("B", structB.get("type").asText());
        assertEquals(8, structB.get("bits").asInt());
        
        JsonNode fieldsB = structB.get("fields");
        assertEquals(2, fieldsB.size());
        
        // 字段 c: uint4, offset 0
        assertEquals("c", fieldsB.get(0).get("name").asText());
        assertEquals("uint4", fieldsB.get(0).get("type").asText());
        assertEquals(4, fieldsB.get(0).get("bits").asInt());
        assertEquals(0, fieldsB.get(0).get("offset").asInt());
        
        // 字段 d: uint4, offset 4
        assertEquals("d", fieldsB.get(1).get("name").asText());
        assertEquals("uint4", fieldsB.get(1).get("type").asText());
        assertEquals(4, fieldsB.get(1).get("bits").asInt());
        assertEquals(4, fieldsB.get(1).get("offset").asInt());
        
        // ==================== 验证结构体 C（DSL语法引用）====================
        JsonNode structC = findStructByType(structs, "C");
        assertNotNull(structC, "Struct C should exist");
        assertEquals("", structC.get("name").asText());
        assertEquals("C", structC.get("type").asText());
        assertEquals(16, structC.get("bits").asInt(), "C should be 16 bits (8 + 8)");
        
        JsonNode fieldsC = structC.get("fields");
        assertEquals(2, fieldsC.size());
        
        // 字段 ref_a: 引用结构体 A
        JsonNode refA = fieldsC.get(0);
        assertEquals("ref_a", refA.get("name").asText());
        assertEquals("A", refA.get("type").asText(), "ref_a type should be A");
        assertEquals(8, refA.get("bits").asInt());
        assertEquals(0, refA.get("offset").asInt());
        assertTrue(refA.has("fields"), "ref_a should have nested fields");
        
        // 验证 ref_a 的嵌套字段
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
        
        // 字段 ref_b: 引用结构体 B
        JsonNode refB = fieldsC.get(1);
        assertEquals("ref_b", refB.get("name").asText());
        assertEquals("B", refB.get("type").asText(), "ref_b type should be B");
        assertEquals(8, refB.get("bits").asInt());
        assertEquals(8, refB.get("offset").asInt(), "ref_b offset should be 8");
        assertTrue(refB.has("fields"), "ref_b should have nested fields");
        
        // 验证 ref_b 的嵌套字段
        JsonNode refBFields = refB.get("fields");
        assertEquals(2, refBFields.size());
        
        JsonNode refB_fieldC = refBFields.get(0);
        assertEquals("c", refB_fieldC.get("name").asText());
        assertEquals("uint4", refB_fieldC.get("type").asText());
        assertEquals(4, refB_fieldC.get("bits").asInt());
        assertEquals(8, refB_fieldC.get("offset").asInt(), "c offset should be absolute 8");
        
        JsonNode refB_fieldD = refBFields.get(1);
        assertEquals("d", refB_fieldD.get("name").asText());
        assertEquals("uint4", refB_fieldD.get("type").asText());
        assertEquals(4, refB_fieldD.get("bits").asInt());
        assertEquals(12, refB_fieldD.get("offset").asInt(), "d offset should be absolute 12");
        
        // ==================== 验证结构体 D（匿名嵌套）====================
        JsonNode structD = findStructByType(structs, "D");
        assertNotNull(structD, "Struct D should exist");
        assertEquals("", structD.get("name").asText());
        assertEquals("D", structD.get("type").asText());
        assertEquals(32, structD.get("bits").asInt(), "D should be 32 bits (16 + 16)");
        
        JsonNode fieldsD = structD.get("fields");
        assertEquals(2, fieldsD.size());
        
        // 字段 g: 匿名结构体
        JsonNode fieldG = fieldsD.get(0);
        assertEquals("g", fieldG.get("name").asText());
        assertEquals("", fieldG.get("type").asText(), "Anonymous struct type should be empty");
        assertEquals(16, fieldG.get("bits").asInt());
        assertEquals(0, fieldG.get("offset").asInt());
        assertTrue(fieldG.has("fields"), "g should have nested fields");
        
        // 验证 g 的嵌套字段
        JsonNode gFields = fieldG.get("fields");
        assertEquals(2, gFields.size());
        
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
        
        // 字段 j: 匿名联合体
        JsonNode fieldJ = fieldsD.get(1);
        assertEquals("j", fieldJ.get("name").asText());
        assertEquals("", fieldJ.get("type").asText(), "Anonymous union type should be empty");
        assertEquals(16, fieldJ.get("bits").asInt());
        assertEquals(16, fieldJ.get("offset").asInt(), "j offset should be 16");
        assertTrue(fieldJ.has("fields"), "j should have nested fields");
        
        // 验证 j 的嵌套字段（union 内所有字段共享相同的绝对偏移量）
        JsonNode jFields = fieldJ.get("fields");
        assertEquals(2, jFields.size());
        
        // 字段 h: uint16, offset 应该是绝对偏移量 16
        assertEquals("h", jFields.get(0).get("name").asText());
        assertEquals("uint16", jFields.get(0).get("type").asText());
        assertEquals(16, jFields.get(0).get("bits").asInt());
        assertEquals(16, jFields.get(0).get("offset").asInt(), "h offset should be absolute 16");
        
        // 字段 i: uint16, offset 应该是绝对偏移量 16（union 内共享）
        assertEquals("i", jFields.get(1).get("name").asText());
        assertEquals("uint16", jFields.get(1).get("type").asText());
        assertEquals(16, jFields.get(1).get("bits").asInt());
        assertEquals(16, jFields.get(1).get("offset").asInt(), "i offset should be absolute 16");
    }
    
    /**
     * 根据 type 字段查找结构体
     */
    private JsonNode findStructByType(JsonNode structs, String type) {
        for (JsonNode struct : structs) {
            if (type.equals(struct.get("type").asText())) {
                return struct;
            }
        }
        return null;
    }
}
