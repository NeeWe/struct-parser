package com.structparser.generator;

import com.structparser.model.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

/**
 * JSON 输出生成器 - 使用 JDK 16+ Record 和 Stream API
 * 输出格式：简单字段对象在一行内显示，嵌套结构展开
 */
public class JsonGenerator {
    
    private static final String INDENT = "  ";
    
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
        writer.write("{\n");
        
        // 结构体
        writer.write(INDENT + "\"structs\" : [\n");
        List<String> structJsons = result.structs().stream()
            .map(this::convertStruct)
            .toList();
        for (int i = 0; i < structJsons.size(); i++) {
            writer.write(INDENT + INDENT + structJsons.get(i));
            if (i < structJsons.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write(INDENT + "],\n");
        
        // 联合体
        writer.write(INDENT + "\"unions\" : [\n");
        List<String> unionJsons = result.unions().stream()
            .map(this::convertUnion)
            .toList();
        for (int i = 0; i < unionJsons.size(); i++) {
            writer.write(INDENT + INDENT + unionJsons.get(i));
            if (i < unionJsons.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write(INDENT + "]");
        
        // typedefs
        if (!result.typedefs().isEmpty()) {
            writer.write(",\n" + INDENT + "\"typedefs\" : " + convertMap(result.typedefs()));
        }
        
        // 错误
        if (result.hasErrors()) {
            writer.write(",\n" + INDENT + "\"errors\" : " + convertList(result.errors()));
        }
        
        writer.write("\n}\n");
    }
    
    private String convertStruct(Struct struct) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"name\" : \"\",\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"type\" : \"").append(struct.anonymous() ? "" : struct.name()).append("\",\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"bits\" : ").append(struct.totalBits()).append(",\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"fields\" : [");
        
        List<String> fieldJsons = struct.fields().stream()
            .map(this::convertField)
            .toList();
        
        if (fieldJsons.isEmpty()) {
            sb.append("]\n");
        } else {
            sb.append("\n");
            for (int i = 0; i < fieldJsons.size(); i++) {
                sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(fieldJsons.get(i));
                if (i < fieldJsons.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(INDENT).append(INDENT).append(INDENT).append("]\n");
        }
        
        sb.append(INDENT).append(INDENT).append("}");
        return sb.toString();
    }
    
    private String convertUnion(Union union) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"name\" : \"\",\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"type\" : \"").append(union.anonymous() ? "" : union.name()).append("\",\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"bits\" : ").append(union.totalBits()).append(",\n");
        sb.append(INDENT).append(INDENT).append(INDENT).append("\"fields\" : [");
        
        List<String> fieldJsons = union.fields().stream()
            .map(this::convertField)
            .toList();
        
        if (fieldJsons.isEmpty()) {
            sb.append("]\n");
        } else {
            sb.append("\n");
            for (int i = 0; i < fieldJsons.size(); i++) {
                sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(fieldJsons.get(i));
                if (i < fieldJsons.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(INDENT).append(INDENT).append(INDENT).append("]\n");
        }
        
        sb.append(INDENT).append(INDENT).append("}");
        return sb.toString();
    }
    
    private String convertField(Field field) {
        StringBuilder sb = new StringBuilder();
        
        // 如果有嵌套的 struct
        if (field.nestedStruct() != null) {
            var nested = field.nestedStruct();
            sb.append("{\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"name\" : \"").append(field.name()).append("\",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"type\" : \"").append(nested.anonymous() ? "" : nested.name()).append("\",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"bits\" : ").append(field.bitWidth()).append(",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"offset\" : ").append(field.bitOffset()).append(",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"fields\" : [");
            
            List<String> nestedFieldJsons = nested.fields().stream()
                .map(this::convertField)
                .toList();
            
            if (nestedFieldJsons.isEmpty()) {
                sb.append("]\n");
            } else {
                sb.append("\n");
                for (int i = 0; i < nestedFieldJsons.size(); i++) {
                    sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(nestedFieldJsons.get(i));
                    if (i < nestedFieldJsons.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("]\n");
            }
            
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("}");
        }
        // 如果有嵌套的 union
        else if (field.nestedUnion() != null) {
            var nested = field.nestedUnion();
            sb.append("{\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"name\" : \"").append(field.name()).append("\",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"type\" : \"").append(nested.anonymous() ? "" : nested.name()).append("\",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"bits\" : ").append(field.bitWidth()).append(",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"offset\" : ").append(field.bitOffset()).append(",\n");
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("\"fields\" : [");
            
            List<String> nestedFieldJsons = nested.fields().stream()
                .map(this::convertField)
                .toList();
            
            if (nestedFieldJsons.isEmpty()) {
                sb.append("]\n");
            } else {
                sb.append("\n");
                for (int i = 0; i < nestedFieldJsons.size(); i++) {
                    sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(nestedFieldJsons.get(i));
                    if (i < nestedFieldJsons.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("]\n");
            }
            
            sb.append(INDENT).append(INDENT).append(INDENT).append(INDENT).append("}");
        }
        // 普通字段 - 紧凑格式，一行显示
        else {
            // 去除匿名 union 成员的特殊前缀
            String displayName = stripAnonymousUnionPrefix(field.name());
            sb.append("{\"name\" : \"").append(displayName).append("\", ");
            sb.append("\"type\" : \"").append(field.type().toString().toLowerCase()).append("\", ");
            sb.append("\"bits\" : ").append(field.bitWidth()).append(", ");
            sb.append("\"offset\" : ").append(field.bitOffset()).append("}");
        }
        
        return sb.toString();
    }
    
    /**
     * 去除匿名 union 成员的特殊前缀
     */
    private String stripAnonymousUnionPrefix(String name) {
        if (name == null) {
            return "";
        }
        // 处理格式: __anon_union_<id>__member__<actual_name>
        if (name.contains("__member__")) {
            int idx = name.indexOf("__member__");
            return name.substring(idx + "__member__".length());
        }
        return name;
    }
    
    private String convertMap(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        List<String> entries = map.entrySet().stream()
            .map(e -> INDENT + INDENT + "\"" + e.getKey() + "\" : \"" + e.getValue() + "\"")
            .toList();
        for (int i = 0; i < entries.size(); i++) {
            sb.append(entries.get(i));
            if (i < entries.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(INDENT).append("}");
        return sb.toString();
    }
    
    private String convertList(List<String> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append(INDENT).append(INDENT).append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(INDENT).append("]");
        return sb.toString();
    }
}
