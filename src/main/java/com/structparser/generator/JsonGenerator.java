package com.structparser.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.structparser.model.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

/**
 * JSON 输出生成器 - 使用 JDK 16+ Record 和 Stream API
 */
public class JsonGenerator {
    
    private final ObjectMapper mapper;
    
    public JsonGenerator() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    /**
     * 生成 JSON 字符串
     */
    public String generate(ParseResult result) {
        try {
            StringWriter writer = new StringWriter();
            generate(result, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate JSON", e);
        }
    }
    
    /**
     * 生成 JSON 到 Writer
     */
    public void generate(ParseResult result, Writer writer) throws IOException {
        var output = new LinkedHashMap<String, Object>();
        
        // 结构体
        output.put("structs", result.structs().stream()
            .map(this::convertStruct)
            .toList());
        
        // 联合体
        output.put("unions", result.unions().stream()
            .map(this::convertUnion)
            .toList());
        
        // typedefs
        if (!result.typedefs().isEmpty()) {
            output.put("typedefs", result.typedefs());
        }
        
        // 错误
        if (result.hasErrors()) {
            output.put("errors", result.errors());
        }
        
        mapper.writeValue(writer, output);
    }
    
    private Map<String, Object> convertStruct(Struct struct) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", struct.name());
        map.put("type", "struct");
        map.put("size_bits", struct.totalBits());
        map.put("anonymous", struct.anonymous());
        map.put("fields", struct.fields().stream()
            .map(this::convertField)
            .toList());
        return map;
    }
    
    private Map<String, Object> convertUnion(Union union) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", union.name());
        map.put("type", "union");
        map.put("size_bits", union.totalBits());
        map.put("anonymous", union.anonymous());
        map.put("fields", union.fields().stream()
            .map(this::convertField)
            .toList());
        return map;
    }
    
    private Map<String, Object> convertField(Field field) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", field.name());
        map.put("type", field.type().toString().toLowerCase());
        map.put("bits", field.bitWidth());
        map.put("offset", field.bitOffset());
        
        // 如果有嵌套的 struct，添加其 fields
        if (field.nestedStruct() != null) {
            map.put("fields", field.nestedStruct().fields().stream()
                .map(this::convertField)
                .toList());
        }
        
        // 如果有嵌套的 union，添加其 fields
        if (field.nestedUnion() != null) {
            map.put("fields", field.nestedUnion().fields().stream()
                .map(this::convertField)
                .toList());
        }
        
        return map;
    }
}
