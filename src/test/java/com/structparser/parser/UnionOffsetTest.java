package com.structparser.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.structparser.generator.JsonGenerator;
import com.structparser.model.ParseResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 Union 内嵌套字段的绝对偏移量计算
 */
public class UnionOffsetTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    public void testPacketHeaderUnionOffsets() throws Exception {
        var parser = new StructParserService();
        String input = """
            struct PacketHeader {
                uint8  version;
                uint8  type;
                uint16 length;
                union {
                    uint32 raw;
                    struct {
                        uint16 low;
                        uint16 high;
                    } words;
                } checksum;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors(), "Should not have errors: " + result.errors());
        assertEquals(1, result.structs().size());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        JsonNode root = objectMapper.readTree(json);
        
        // 打印 JSON 用于调试
        System.out.println("PacketHeader JSON:");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        
        // 验证顶层结构
        JsonNode packetHeader = root.get("structs").get(0);
        assertEquals("PacketHeader", packetHeader.get("type").asText());
        assertEquals(64, packetHeader.get("bits").asInt()); // 8 + 8 + 16 + 32 = 64
        
        JsonNode fields = packetHeader.get("fields");
        assertEquals(4, fields.size());
        
        // 验证前三个字段
        assertEquals(0, fields.get(0).get("offset").asInt());  // version
        assertEquals(8, fields.get(1).get("offset").asInt());  // type
        assertEquals(16, fields.get(2).get("offset").asInt()); // length
        
        // 验证 checksum union 字段
        JsonNode checksumField = fields.get(3);
        assertEquals("checksum", checksumField.get("name").asText());
        assertEquals(32, checksumField.get("offset").asInt()); // checksum 的 offset 应该是 32
        assertEquals(32, checksumField.get("bits").asInt());
        
        // 验证 union 内部的字段
        JsonNode unionFields = checksumField.get("fields");
        assertEquals(2, unionFields.size());
        
        // raw 字段的 offset 应该是 32（绝对偏移）
        JsonNode rawField = unionFields.get(0);
        assertEquals("raw", rawField.get("name").asText());
        assertEquals(32, rawField.get("offset").asInt(), "raw offset should be 32");
        assertEquals(32, rawField.get("bits").asInt());
        
        // words 字段的 offset 应该是 32（绝对偏移）
        JsonNode wordsField = unionFields.get(1);
        assertEquals("words", wordsField.get("name").asText());
        assertEquals(32, wordsField.get("offset").asInt(), "words offset should be 32");
        assertEquals(32, wordsField.get("bits").asInt());
        
        // 验证 words 嵌套 struct 内部的字段
        JsonNode wordsNestedFields = wordsField.get("fields");
        assertEquals(2, wordsNestedFields.size());
        
        // low 字段的 offset 应该是 32（绝对偏移）
        JsonNode lowField = wordsNestedFields.get(0);
        assertEquals("low", lowField.get("name").asText());
        assertEquals(32, lowField.get("offset").asInt(), "low offset should be 32");
        assertEquals(16, lowField.get("bits").asInt());
        
        // high 字段的 offset 应该是 48（绝对偏移，32 + 16）
        JsonNode highField = wordsNestedFields.get(1);
        assertEquals("high", highField.get("name").asText());
        assertEquals(48, highField.get("offset").asInt(), "high offset should be 48");
        assertEquals(16, highField.get("bits").asInt());
    }
    
    @Test
    public void testSimpleUnionOffsets() throws Exception {
        var parser = new StructParserService();
        String input = """
            struct Test {
                uint8 a;
                union {
                    uint16 b;
                    uint16 c;
                } data;
                uint8 d;
            };
            """;
        
        ParseResult result = parser.parse(input);
        assertFalse(result.hasErrors());
        
        var generator = new JsonGenerator();
        String json = generator.generate(result);
        JsonNode root = objectMapper.readTree(json);
        
        System.out.println("\nSimple Union JSON:");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        
        JsonNode testStruct = root.get("structs").get(0);
        JsonNode fields = testStruct.get("fields");
        
        // a: offset 0
        assertEquals(0, fields.get(0).get("offset").asInt());
        
        // data union: offset 8
        JsonNode dataField = fields.get(1);
        assertEquals("data", dataField.get("name").asText());
        assertEquals(8, dataField.get("offset").asInt());
        
        // union 内部字段都应该有 offset 8
        JsonNode unionFields = dataField.get("fields");
        assertEquals(8, unionFields.get(0).get("offset").asInt(), "b offset should be 8");
        assertEquals(8, unionFields.get(1).get("offset").asInt(), "c offset should be 8");
        
        // d: offset 24 (8 + 16)
        assertEquals(24, fields.get(2).get("offset").asInt());
    }
}
