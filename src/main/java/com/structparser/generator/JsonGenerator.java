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
        // 顶层结构的 name 为空字符串
        map.put("name", "");
        // 匿名结构 type 为空字符串，具名结构 type 为名称
        map.put("type", struct.anonymous() ? "" : struct.name());
        map.put("bits", struct.totalBits());
        map.put("fields", struct.fields().stream()
            .map(this::convertField)
            .toList());
        return map;
    }
    
    private Map<String, Object> convertUnion(Union union) {
        var map = new LinkedHashMap<String, Object>();
        // 顶层联合体的 name 为空字符串
        map.put("name", "");
        // 匿名联合体 type 为空字符串，具名联合体 type 为名称
        map.put("type", union.anonymous() ? "" : union.name());
        map.put("bits", union.totalBits());
        map.put("fields", union.fields().stream()
            .map(this::convertField)
            .toList());
        return map;
    }
    
    private Map<String, Object> convertField(Field field) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", field.name());
        
        // 如果有嵌套的 struct
        if (field.nestedStruct() != null) {
            var nested = field.nestedStruct();
            // 匿名结构 type 为空字符串，具名引用使用名称
            map.put("type", nested.anonymous() ? "" : nested.name());
            map.put("bits", field.bitWidth());
            map.put("offset", field.bitOffset());
            map.put("fields", nested.fields().stream()
                .map(this::convertField)
                .toList());
        }
        // 如果有嵌套的 union
        else if (field.nestedUnion() != null) {
            var nested = field.nestedUnion();
            // 匿名联合体 type 为空字符串，具名引用使用名称
            map.put("type", nested.anonymous() ? "" : nested.name());
            map.put("bits", field.bitWidth());
            map.put("offset", field.bitOffset());
            map.put("fields", nested.fields().stream()
                .map(this::convertField)
                .toList());
        }
        // 普通字段
        else {
            map.put("type", field.type().toString().toLowerCase());
            map.put("bits", field.bitWidth());
            map.put("offset", field.bitOffset());
        }
        
        return map;
    }
}
