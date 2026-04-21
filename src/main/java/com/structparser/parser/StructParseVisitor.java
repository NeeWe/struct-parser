package com.structparser.parser;

import com.structparser.model.*;
import com.structparser.StructParserBaseVisitor;
import com.structparser.StructParserLexer;
import com.structparser.StructParserParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.*;

/**
 * ANTLR4 Visitor 实现 - 解析 struct/union 定义
 * 
 * 核心规则：
 * 1. 所有字段的 offset 都是相对于最外层结构体的绝对偏移
 * 2. 匿名嵌套（无字段名）时字段直接展开到父级
 * 3. 具名嵌套和引用类型保留嵌套结构并展开 fields
 * 4. Union 内所有成员共享相同的 offset
 * 5. 支持前向引用（两遍扫描）
 */
public class StructParseVisitor extends StructParserBaseVisitor<Object> {
    
    private ParseResult result = ParseResult.empty();
    private final Map<String, String> typedefs = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    
    // 两遍扫描支持
    private final Set<String> declaredNames = new HashSet<>();  // 所有已声明的类型名称
    private final Set<String> currentlyParsing = new HashSet<>();  // 正在解析的类型（检测循环引用）
    
    public ParseResult getResult() {
        errors.forEach(e -> result = result.withError(e));
        return result;
    }
    
    @Override
    public Object visitProgram(StructParserParser.ProgramContext ctx) {
        // 第一遍：收集所有顶层声明的名称
        collectDeclaredNames(ctx.item());
        
        // 第二遍：正常解析
        ctx.item().forEach(this::visit);
        return result;
    }
    
    /**
     * 第一遍扫描：收集所有顶层 struct/union 的名称
     */
    private void collectDeclaredNames(List<StructParserParser.ItemContext> items) {
        for (var item : items) {
            var decl = item.declaration();
            if (decl == null) continue;
            
            if (decl.structDeclaration() != null) {
                var structCtx = decl.structDeclaration();
                if (structCtx.Identifier() != null) {
                    declaredNames.add(structCtx.Identifier().getText());
                }
            } else if (decl.unionDeclaration() != null) {
                var unionCtx = decl.unionDeclaration();
                if (unionCtx.Identifier() != null) {
                    declaredNames.add(unionCtx.Identifier().getText());
                }
            }
        }
    }
    
    @Override
    public Object visitStructDeclaration(StructParserParser.StructDeclarationContext ctx) {
        String name = ctx.Identifier() != null ? ctx.Identifier().getText() : null;
        boolean anonymous = (name == null);
        
        // 检查重复定义
        if (name != null && result.getStructByName(name) != null) {
            addError(ctx, "Duplicate struct: " + name);
            return null;
        }
        
        // 标记正在解析（检测循环引用）
        if (name != null) {
            currentlyParsing.add(name);
        }
        
        // 解析字段
        List<Field> fields = parseFields(ctx.fieldList(), 0);
        
        // 移除标记
        if (name != null) {
            currentlyParsing.remove(name);
        }
        
        var struct = new Struct(name, fields, anonymous);
        
        // 顶层 struct 添加到结果中
        if (name != null || isTopLevel(ctx)) {
            result = result.withStruct(struct);
        }
        
        return struct;
    }
    
    @Override
    public Object visitUnionDeclaration(StructParserParser.UnionDeclarationContext ctx) {
        String name = ctx.Identifier() != null ? ctx.Identifier().getText() : null;
        boolean anonymous = (name == null);
        
        // 检查重复定义
        if (name != null && result.getUnionByName(name) != null) {
            addError(ctx, "Duplicate union: " + name);
            return null;
        }
        
        // 标记正在解析
        if (name != null) {
            currentlyParsing.add(name);
        }
        
        // 解析字段
        List<Field> fields = parseUnionFields(ctx.fieldList(), 0);
        
        // 移除标记
        if (name != null) {
            currentlyParsing.remove(name);
        }
        
        var union = new Union(name, fields, anonymous);
        
        // 顶层 union 添加到结果中
        if (name != null || isTopLevel(ctx)) {
            result = result.withUnion(union);
        }
        
        return union;
    }
    
    /**
     * 解析 struct 的字段列表
     * @param baseOffset 基础偏移量（相对于最外层结构体）
     */
    private List<Field> parseFields(StructParserParser.FieldListContext ctx, int baseOffset) {
        if (ctx == null) return List.of();
        
        List<Field> fields = new ArrayList<>();
        int currentOffset = baseOffset;
        
        for (var fieldCtx : ctx.field()) {
            List<Field> parsedFields = parseField(fieldCtx, currentOffset);
            
            if (parsedFields.isEmpty()) {
                continue;
            }
            
            // 添加到结果中
            fields.addAll(parsedFields);
            
            // 计算偏移量增加
            if (parsedFields.size() == 1) {
                // 单个字段：正常增加
                currentOffset += parsedFields.get(0).bitWidth();
            } else {
                // 多个字段：检查是否是匿名 union（所有字段 offset 相同）
                boolean isAnonymousUnion = parsedFields.stream()
                    .map(Field::bitOffset)
                    .distinct()
                    .count() == 1;
                
                if (isAnonymousUnion) {
                    // 匿名 union：增加最大宽度
                    int maxWidth = parsedFields.stream()
                        .mapToInt(Field::bitWidth)
                        .max()
                        .orElse(0);
                    currentOffset += maxWidth;
                } else {
                    // 匿名 struct：增加所有字段宽度之和
                    int totalWidth = parsedFields.stream()
                        .mapToInt(Field::bitWidth)
                        .sum();
                    currentOffset += totalWidth;
                }
            }
        }
        
        return fields;
    }
    
    /**
     * 解析 union 的字段列表
     * @param baseOffset 基础偏移量（union 的起始位置）
     */
    private List<Field> parseUnionFields(StructParserParser.FieldListContext ctx, int baseOffset) {
        if (ctx == null) return List.of();
        
        List<Field> fields = new ArrayList<>();
        int maxBits = 0;
        
        // Union 内所有字段共享相同的 offset
        for (var fieldCtx : ctx.field()) {
            List<Field> parsedFields = parseField(fieldCtx, baseOffset);
            for (Field f : parsedFields) {
                fields.add(f);
                maxBits = Math.max(maxBits, f.bitWidth());
            }
        }
        
        return fields;
    }
    
    /**
     * 解析单个字段，返回字段列表（匿名展开时可能返回多个）
     */
    private List<Field> parseField(StructParserParser.FieldContext ctx, int offset) {
        List<Field> result = new ArrayList<>();
        
        // 情况1: 匿名 struct { ... } name?;
        if (isKeyword(ctx, 0, "struct") && ctx.fieldList() != null) {
            String fieldName = ctx.fieldName() != null ? ctx.fieldName().getText() : null;
            List<Field> nestedFields = parseFields(ctx.fieldList(), offset);
            
            if (fieldName == null || fieldName.isEmpty()) {
                // 匿名无名称：展开字段到父级
                return nestedFields;
            } else {
                // 有名称：作为嵌套字段
                Struct nestedStruct = new Struct(null, nestedFields, true);
                result.add(Field.withNestedStruct(fieldName, nestedStruct.totalBits(), offset, nestedStruct));
                return result;
            }
        }
        
        // 情况2: 匿名 union { ... } name?;
        if (isKeyword(ctx, 0, "union") && ctx.fieldList() != null) {
            String fieldName = ctx.fieldName() != null ? ctx.fieldName().getText() : null;
            List<Field> nestedFields = parseUnionFields(ctx.fieldList(), offset);
            
            if (fieldName == null || fieldName.isEmpty()) {
                // 匿名无名称：展开字段到父级
                return nestedFields;
            } else {
                // 有名称：作为嵌套字段
                Union nestedUnion = new Union(null, nestedFields, true);
                result.add(Field.withNestedUnion(fieldName, nestedUnion.totalBits(), offset, nestedUnion));
                return result;
            }
        }
        
        // 情况3: struct/union Name name; (标准 C 语法)
        if (isKeyword(ctx, 0, "struct") && ctx.Identifier() != null && ctx.fieldName() != null) {
            String typeName = ctx.Identifier().getText();
            String fieldName = ctx.fieldName().getText();
            Field field = resolveStructReference(typeName, fieldName, offset, ctx);
            if (field != null) result.add(field);
            return result;
        }
        
        if (isKeyword(ctx, 0, "union") && ctx.Identifier() != null && ctx.fieldName() != null) {
            String typeName = ctx.Identifier().getText();
            String fieldName = ctx.fieldName().getText();
            Field field = resolveUnionReference(typeName, fieldName, offset, ctx);
            if (field != null) result.add(field);
            return result;
        }
        
        // 情况4: TypeName name; (DSL 语法，直接引用)
        if (ctx.Identifier() != null && ctx.fieldName() != null) {
            // 检查是否有多个 Identifier（struct/union Name name 的情况）
            int identifierCount = 0;
            String typeName = null;
            for (int i = 0; i < ctx.getChildCount(); i++) {
                if (ctx.getChild(i) instanceof org.antlr.v4.runtime.tree.TerminalNode) {
                    var token = (org.antlr.v4.runtime.tree.TerminalNode) ctx.getChild(i);
                    if (token.getSymbol().getType() == StructParserLexer.Identifier) {
                        identifierCount++;
                        if (identifierCount == 1) {
                            typeName = token.getText();
                        }
                    }
                }
            }
            
            if (identifierCount == 1 && typeName != null) {
                String fieldName = ctx.fieldName().getText();
                Field field = resolveTypeReference(typeName, fieldName, offset, ctx);
                if (field != null) result.add(field);
                return result;
            }
        }
        
        // 情况5: 基础类型字段 uintN name;
        if (ctx.typeSpecifier() != null && ctx.fieldName() != null) {
            String fieldName = ctx.fieldName().getText();
            
            // 检查 typeSpecifier 是否是 Identifier（如 uint33）
            if (ctx.typeSpecifier().Identifier() != null) {
                String typeName = ctx.typeSpecifier().Identifier().getText();
                
                // 检查是否匹配 uintN 格式
                if (typeName.matches("^uint(\\d+)$")) {
                    int width = Integer.parseInt(typeName.substring(4));
                    if (width < 1 || width > 32) {
                        addError(ctx, "Invalid uint width: " + width + " (must be 1-32)");
                        result.add(new Field(fieldName, Type.CUSTOM, 0, offset, null, null));
                        return result;
                    }
                    Type type = Type.fromBitWidth(width);
                    result.add(new Field(fieldName, type, width, offset, null, null));
                    return result;
                }
                
                // 尝试查找 struct/union 引用
                Field field = resolveTypeReference(typeName, fieldName, offset, ctx);
                if (field != null) {
                    result.add(field);
                    return result;
                }
                
                // 未知类型
                result.add(new Field(fieldName, Type.CUSTOM, 0, offset, null, null));
                return result;
            }
            
            // uint N 格式（两个 token）
            Type type = parseType(ctx.typeSpecifier());
            int width = (type != null && type.isPrimitive()) ? type.getBitWidth() : 0;
            result.add(new Field(fieldName, type != null ? type : Type.CUSTOM, width, offset, null, null));
            return result;
        }
        
        return result;
    }
    
    /**
     * 解析类型引用（struct 或 union）
     */
    private Field resolveTypeReference(String typeName, String fieldName, int offset, StructParserParser.FieldContext ctx) {
        // 检查循环引用
        if (currentlyParsing.contains(typeName)) {
            addError(ctx, "Circular reference detected: " + typeName);
            return null;
        }
        
        // 先查找 struct
        var structRef = result.getStructByName(typeName);
        if (structRef != null) {
            // 创建新的 struct，字段 offset 相对于当前 offset
            Struct expandedStruct = createExpandedStruct(structRef, offset);
            return Field.withNestedStruct(fieldName, structRef.totalBits(), offset, expandedStruct);
        }
        
        // 再查找 union
        var unionRef = result.getUnionByName(typeName);
        if (unionRef != null) {
            // 创建新的 union，字段 offset 相对于当前 offset
            Union expandedUnion = createExpandedUnion(unionRef, offset);
            return Field.withNestedUnion(fieldName, unionRef.totalBits(), offset, expandedUnion);
        }
        
        // 未找到，检查是否是前向引用
        if (declaredNames.contains(typeName)) {
            addError(ctx, "Forward reference not allowed: " + typeName);
        }
        
        return null;
    }
    
    private Field resolveStructReference(String structName, String fieldName, int offset, StructParserParser.FieldContext ctx) {
        if (currentlyParsing.contains(structName)) {
            addError(ctx, "Circular reference detected: " + structName);
            return null;
        }
        
        var structRef = result.getStructByName(structName);
        if (structRef != null) {
            Struct expandedStruct = createExpandedStruct(structRef, offset);
            return Field.withNestedStruct(fieldName, structRef.totalBits(), offset, expandedStruct);
        }
        
        addError(ctx, "Undefined struct: " + structName);
        return null;
    }
    
    private Field resolveUnionReference(String unionName, String fieldName, int offset, StructParserParser.FieldContext ctx) {
        if (currentlyParsing.contains(unionName)) {
            addError(ctx, "Circular reference detected: " + unionName);
            return null;
        }
        
        var unionRef = result.getUnionByName(unionName);
        if (unionRef != null) {
            Union expandedUnion = createExpandedUnion(unionRef, offset);
            return Field.withNestedUnion(fieldName, unionRef.totalBits(), offset, expandedUnion);
        }
        
        addError(ctx, "Undefined union: " + unionName);
        return null;
    }
    
    /**
     * 创建展开的 struct，字段 offset 基于 baseOffset 重新计算
     */
    private Struct createExpandedStruct(Struct struct, int baseOffset) {
        List<Field> newFields = new ArrayList<>();
        int currentOffset = baseOffset;
        
        for (Field field : struct.fields()) {
            if (field.nestedStruct() != null) {
                // 嵌套 struct，递归创建
                Struct nestedExpanded = createExpandedStruct(field.nestedStruct(), currentOffset);
                newFields.add(Field.withNestedStruct(field.name(), field.bitWidth(), currentOffset, nestedExpanded));
            } else if (field.nestedUnion() != null) {
                // 嵌套 union，递归创建
                Union nestedExpanded = createExpandedUnion(field.nestedUnion(), currentOffset);
                newFields.add(Field.withNestedUnion(field.name(), field.bitWidth(), currentOffset, nestedExpanded));
            } else {
                // 普通字段，创建新字段并设置绝对 offset
                newFields.add(new Field(field.name(), field.type(), field.bitWidth(), currentOffset, null, null));
            }
            currentOffset += field.bitWidth();
        }
        
        return new Struct(struct.name(), newFields, struct.anonymous());
    }
    
    /**
     * 创建展开的 union，字段 offset 都等于 baseOffset
     */
    private Union createExpandedUnion(Union union, int baseOffset) {
        List<Field> newFields = new ArrayList<>();
        
        for (Field field : union.fields()) {
            if (field.nestedStruct() != null) {
                Struct nestedExpanded = createExpandedStruct(field.nestedStruct(), baseOffset);
                newFields.add(Field.withNestedStruct(field.name(), field.bitWidth(), baseOffset, nestedExpanded));
            } else if (field.nestedUnion() != null) {
                Union nestedExpanded = createExpandedUnion(field.nestedUnion(), baseOffset);
                newFields.add(Field.withNestedUnion(field.name(), field.bitWidth(), baseOffset, nestedExpanded));
            } else {
                newFields.add(new Field(field.name(), field.type(), field.bitWidth(), baseOffset, null, null));
            }
        }
        
        return new Union(union.name(), newFields, union.anonymous());
    }
    
    /**
     * 检查 field 的第 index 个子节点是否是指定关键字
     */
    private boolean isKeyword(StructParserParser.FieldContext ctx, int index, String keyword) {
        if (ctx.getChildCount() <= index) return false;
        String text = ctx.getChild(index).getText();
        return text.equals(keyword);
    }
    
    /**
     * 检查是否是顶层声明（不在任何 struct/union 内部）
     */
    private boolean isTopLevel(ParserRuleContext ctx) {
        return ctx.getParent() instanceof StructParserParser.DeclarationContext;
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
    
    /**
     * 解析类型说明符
     */
    private Type parseType(StructParserParser.TypeSpecifierContext ctx) {
        if (ctx.IntegerLiteral() != null) {
            int width = Integer.parseInt(ctx.IntegerLiteral().getText());
            // 验证宽度范围：1-32
            if (width < 1 || width > 32) {
                addError(ctx, "Invalid uint width: " + width + " (must be 1-32)");
                return Type.CUSTOM;
            }
            try {
                return Type.fromBitWidth(width);
            } catch (IllegalArgumentException e) {
                addError(ctx, "Invalid width: " + width);
                return Type.CUSTOM;
            }
        }
        
        if (ctx.Identifier() != null) {
            String name = ctx.Identifier().getText();
            // 检查是否是 typedef
            if (typedefs.containsKey(name)) {
                return Type.CUSTOM;
            }
            // 尝试解析为 uintN
            try {
                return Type.fromString(name);
            } catch (IllegalArgumentException e) {
                // 不是基础类型，可能是 struct/union 引用
                return Type.CUSTOM;
            }
        }
        
        return Type.CUSTOM;
    }
    
    private void addError(ParserRuleContext ctx, String msg) {
        int line = ctx.getStart().getLine();
        int col = ctx.getStart().getCharPositionInLine();
        errors.add(String.format("Line %d:%d - %s", line, col, msg));
    }
}
