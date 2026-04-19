package com.structparser.parser;

import com.structparser.model.*;
import com.structparser.StructParserBaseVisitor;
import com.structparser.StructParserParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.*;

/**
 * ANTLR4 Visitor 实现
 */
public class StructParseVisitor extends StructParserBaseVisitor<Object> {
    
    private ParseResult result = ParseResult.empty();
    private final Map<String, String> typedefs = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final Deque<Context> stack = new ArrayDeque<>();
    
    public ParseResult getResult() {
        errors.forEach(e -> result = result.withError(e));
        return result;
    }
    
    @Override
    public Object visitProgram(StructParserParser.ProgramContext ctx) {
        ctx.declaration().forEach(this::visit);
        return result;
    }
    
    @Override
    public Object visitStructDeclaration(StructParserParser.StructDeclarationContext ctx) {
        String name = ctx.Identifier() != null ? ctx.Identifier().getText() : null;
        
        if (name != null && result.getStructByName(name) != null) {
            addError(ctx, "Duplicate struct: " + name);
            return null;
        }
        
        var fields = parseFields(ctx.fieldList());
        var struct = new Struct(name, fields, name == null);
        
        if (stack.isEmpty()) {
            result = result.withStruct(struct);
        } else {
            // 嵌套的 struct，添加到父字段中并保留嵌套信息
            addNestedField(name, Type.STRUCT, struct.totalBits(), struct, null);
        }
        return struct;
    }
    
    @Override
    public Object visitUnionDeclaration(StructParserParser.UnionDeclarationContext ctx) {
        String name = ctx.Identifier() != null ? ctx.Identifier().getText() : null;
        
        if (name != null && result.getUnionByName(name) != null) {
            addError(ctx, "Duplicate union: " + name);
            return null;
        }
        
        var fields = parseUnionFields(ctx.fieldList());
        var union = new Union(name, fields, name == null);
        
        if (stack.isEmpty()) {
            result = result.withUnion(union);
        } else {
            // 嵌套的 union，添加到父字段中并保留嵌套信息
            addNestedField(name, Type.UNION, union.totalBits(), null, union);
        }
        return union;
    }
    
    @Override
    public Object visitField(StructParserParser.FieldContext ctx) {
        // 嵌套定义
        if (ctx.structDeclaration() != null) return visit(ctx.structDeclaration());
        if (ctx.unionDeclaration() != null) return visit(ctx.unionDeclaration());
        
        String first = ctx.getChild(0).getText();
        
        // 匿名结构体: struct { ... } name;
        if (first.equals("struct") && ctx.fieldList() != null) {
            String fieldName = ctx.fieldName().getText();
            var fields = parseFields(ctx.fieldList());
            var struct = new Struct(null, fields, true);
            // 不添加到顶层 structs，只作为嵌套字段
            addNestedField(fieldName, Type.STRUCT, struct.totalBits(), struct, null);
            return null;
        }
        
        // 匿名联合体: union { ... } name;
        if (first.equals("union") && ctx.fieldList() != null) {
            String fieldName = ctx.fieldName().getText();
            var fields = parseUnionFields(ctx.fieldList());
            var union = new Union(null, fields, true);
            // 不添加到顶层 unions，只作为嵌套字段
            addNestedField(fieldName, Type.UNION, union.totalBits(), null, union);
            return null;
        }
        
        // 引用结构体: struct StructName name;
        if (first.equals("struct") && ctx.Identifier() != null) {
            String structName = ctx.Identifier().getText();
            String fieldName = ctx.fieldName().getText();
            var ref = result.getStructByName(structName);
            if (ref != null) {
                // 保留嵌套的 struct 信息
                addNestedField(fieldName, Type.STRUCT, ref.totalBits(), ref, null);
            } else {
                addError(ctx, "Undefined struct: " + structName);
                addField(fieldName, Type.STRUCT, 0);
            }
            return null;
        }
        
        // 引用联合体: union UnionName name;
        if (first.equals("union") && ctx.Identifier() != null) {
            String unionName = ctx.Identifier().getText();
            String fieldName = ctx.fieldName().getText();
            var ref = result.getUnionByName(unionName);
            if (ref != null) {
                // 保留嵌套的 union 信息
                addNestedField(fieldName, Type.UNION, ref.totalBits(), null, ref);
            } else {
                addError(ctx, "Undefined union: " + unionName);
                addField(fieldName, Type.UNION, 0);
            }
            return null;
        }
        
        // DSL语法：直接使用类型名称引用结构体/联合体 (例如: A ref_a;)
        // 注意：需要检查 typeSpecifier 是否是 Identifier（而不是 uintN）
        if (!first.equals("struct") && !first.equals("union") && ctx.typeSpecifier() != null) {
            // 检查 typeSpecifier 是否是 Identifier 类型
            if (ctx.typeSpecifier().Identifier() != null) {
                String typeName = ctx.typeSpecifier().Identifier().getText();
                String fieldName = ctx.fieldName().getText();
                
                // 先尝试查找结构体
                var structRef = result.getStructByName(typeName);
                if (structRef != null) {
                    addNestedField(fieldName, Type.STRUCT, structRef.totalBits(), structRef, null);
                    return null;
                }
                
                // 再尝试查找联合体
                var unionRef = result.getUnionByName(typeName);
                if (unionRef != null) {
                    addNestedField(fieldName, Type.UNION, unionRef.totalBits(), null, unionRef);
                    return null;
                }
                
                // 如果都找不到，作为普通字段处理（可能是 typedef 或未知类型）
                // 继续执行下面的普通字段处理逻辑
            }
        }
        
        // 普通字段
        if (ctx.typeSpecifier() != null) {
            var type = parseType(ctx.typeSpecifier());
            String name = ctx.fieldName().getText();
            int width = type != null && type.isPrimitive() ? type.getBitWidth() : 0;
            addField(name, type != null ? type : Type.CUSTOM, width);
        }
        return null;
    }
    
    @Override
    public Object visitTypedefDeclaration(StructParserParser.TypedefDeclarationContext ctx) {
        String alias = ctx.Identifier().getText();
        if (typedefs.containsKey(alias)) {
            addError(ctx, "Duplicate typedef: " + alias);
            return null;
        }
        String actualType = ctx.typeDefinition().getText();
        typedefs.put(alias, actualType);
        result = result.withTypedef(alias, actualType);
        return null;
    }
    
    // ============ Helper Methods ============
    
    private List<Field> parseFields(StructParserParser.FieldListContext ctx) {
        if (ctx == null) return List.of();
        
        stack.push(new Context(new ArrayList<>()));
        ctx.field().forEach(this::visit);
        var context = stack.pop();
        
        // 计算偏移量
        var result = new ArrayList<Field>();
        int offset = 0;
        for (Field f : context.fields) {
            // 为字段设置偏移量，并递归处理嵌套结构
            Field updatedField = setFieldOffset(f, offset);
            result.add(updatedField);
            offset += f.bitWidth();
        }
        return result;
    }
    
    private List<Field> parseUnionFields(StructParserParser.FieldListContext ctx) {
        if (ctx == null) return List.of();
        
        stack.push(new Context(new ArrayList<>()));
        ctx.field().forEach(this::visit);
        var context = stack.pop();
        
        // Union 内的字段在创建时 offset 为 0，后续会在父级结构中更新
        return context.fields.stream()
            .map(f -> new Field(f.name(), f.type(), f.bitWidth(), 0, f.nestedStruct(), f.nestedUnion()))
            .toList();
    }
    
    private void addField(String name, Type type, int width) {
        if (!stack.isEmpty()) {
            stack.peek().fields.add(Field.of(name, type, width));
        }
    }
    
    private void addNestedField(String name, Type type, int width, Struct nestedStruct, Union nestedUnion) {
        if (!stack.isEmpty()) {
            if (nestedStruct != null) {
                stack.peek().fields.add(Field.withNestedStruct(name, width, 0, nestedStruct));
            } else if (nestedUnion != null) {
                stack.peek().fields.add(Field.withNestedUnion(name, width, 0, nestedUnion));
            } else {
                stack.peek().fields.add(Field.of(name, type, width));
            }
        }
    }
    
    /**
     * 为字段设置绝对偏移量，并递归更新嵌套结构的偏移量
     */
    private Field setFieldOffset(Field field, int offset) {
        if (field.nestedStruct() != null) {
            // 递归更新嵌套 struct 内部所有字段的偏移量
            Struct updatedStruct = updateStructOffsets(field.nestedStruct(), offset);
            return new Field(field.name(), field.type(), field.bitWidth(), offset, updatedStruct, null);
        } else if (field.nestedUnion() != null) {
            // 递归更新嵌套 union 内部所有字段的偏移量
            Union updatedUnion = updateUnionOffsets(field.nestedUnion(), offset);
            return new Field(field.name(), field.type(), field.bitWidth(), offset, null, updatedUnion);
        } else {
            // 普通字段
            return new Field(field.name(), field.type(), field.bitWidth(), offset, null, null);
        }
    }
    
    /**
     * 递归更新 struct 内部所有字段的绝对偏移量
     */
    private Struct updateStructOffsets(Struct struct, int baseOffset) {
        List<Field> updatedFields = new ArrayList<>();
        int currentOffset = baseOffset;
        for (Field field : struct.fields()) {
            Field updatedField = setFieldOffset(field, currentOffset);
            updatedFields.add(updatedField);
            currentOffset += field.bitWidth();
        }
        return new Struct(struct.name(), updatedFields, struct.anonymous());
    }
    
    /**
     * 递归更新 union 内部所有字段的绝对偏移量（union 内所有字段共享相同的基偏移量）
     */
    private Union updateUnionOffsets(Union union, int baseOffset) {
        List<Field> updatedFields = union.fields().stream()
            .map(field -> setFieldOffset(field, baseOffset))
            .toList();
        return new Union(union.name(), updatedFields, union.anonymous());
    }
    
    private Type parseType(StructParserParser.TypeSpecifierContext ctx) {
        if (ctx.Identifier() != null) {
            String name = ctx.Identifier().getText();
            if (typedefs.containsKey(name)) return Type.CUSTOM;
            try {
                return Type.fromString(name);
            } catch (IllegalArgumentException e) {
                addError(ctx, "Unknown type: " + name);
                return null;
            }
        }
        if (ctx.IntegerLiteral() != null) {
            int width = Integer.parseInt(ctx.IntegerLiteral().getText());
            try {
                return Type.fromBitWidth(width);
            } catch (IllegalArgumentException e) {
                addError(ctx, "Invalid width: " + width);
                return null;
            }
        }
        return null;
    }
    
    private void addError(ParserRuleContext ctx, String msg) {
        int line = ctx.getStart().getLine();
        int col = ctx.getStart().getCharPositionInLine();
        errors.add(String.format("Line %d:%d - %s", line, col, msg));
    }
    
    private record Context(List<Field> fields) {}
}
