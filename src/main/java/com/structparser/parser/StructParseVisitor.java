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
            // 保留嵌套的 struct/union 信息
            result.add(new Field(f.name(), f.type(), f.bitWidth(), offset, f.nestedStruct(), f.nestedUnion()));
            offset += f.bitWidth();
        }
        return result;
    }
    
    private List<Field> parseUnionFields(StructParserParser.FieldListContext ctx) {
        if (ctx == null) return List.of();
        
        stack.push(new Context(new ArrayList<>()));
        ctx.field().forEach(this::visit);
        var context = stack.pop();
        
        // 联合体偏移量都为0
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
