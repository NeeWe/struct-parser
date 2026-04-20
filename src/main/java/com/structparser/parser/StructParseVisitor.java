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
    // 用于检测交叉引用：记录正在解析的结构体名称
    private final Set<String> currentlyParsing = new HashSet<>();
    // 所有已声明的顶层结构体和联合体名称（用于区分未定义和交叉引用）
    private final Set<String> declaredNames = new HashSet<>();
    
    public ParseResult getResult() {
        errors.forEach(e -> result = result.withError(e));
        return result;
    }
    
    @Override
    public Object visitProgram(StructParserParser.ProgramContext ctx) {
        // 第一遍：收集所有顶层结构体和联合体的名称
        collectDeclaredNames(ctx.declaration());
        
        // 第二遍：正常解析
        ctx.declaration().forEach(this::visit);
        return result;
    }
    
    /**
     * 第一遍扫描：收集所有顶层结构体和联合体的名称
     */
    private void collectDeclaredNames(List<StructParserParser.DeclarationContext> declarations) {
        for (var decl : declarations) {
            if (decl.structDeclaration() != null) {
                String name = decl.structDeclaration().Identifier() != null ? 
                    decl.structDeclaration().Identifier().getText() : null;
                if (name != null) {
                    declaredNames.add(name);
                }
            } else if (decl.unionDeclaration() != null) {
                String name = decl.unionDeclaration().Identifier() != null ? 
                    decl.unionDeclaration().Identifier().getText() : null;
                if (name != null) {
                    declaredNames.add(name);
                }
            }
        }
    }
    
    @Override
    public Object visitStructDeclaration(StructParserParser.StructDeclarationContext ctx) {
        String name = ctx.Identifier() != null ? ctx.Identifier().getText() : null;
        
        if (name != null && result.getStructByName(name) != null) {
            addError(ctx, "Duplicate struct: " + name);
            return null;
        }
        
        // 标记当前正在解析的结构体
        if (name != null) {
            currentlyParsing.add(name);
        }
        
        var fields = parseFields(ctx.fieldList());
        var struct = new Struct(name, fields, name == null);
        
        // 移除标记
        if (name != null) {
            currentlyParsing.remove(name);
        }
        
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
        
        // 标记当前正在解析的联合体
        if (name != null) {
            currentlyParsing.add(name);
        }
        
        var fields = parseUnionFields(ctx.fieldList());
        var union = new Union(name, fields, name == null);
        
        // 移除标记
        if (name != null) {
            currentlyParsing.remove(name);
        }
        
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
        
        // 安全检查：确保有子节点
        if (ctx.getChildCount() == 0) {
            addError(ctx, "Invalid field definition");
            return null;
        }
        
        String first = ctx.getChild(0).getText();
        
        // 匿名结构体: struct { ... } name; 或 struct { ... }; (无名称)
        if (first.equals("struct") && ctx.fieldList() != null) {
            var fields = parseFields(ctx.fieldList());
            var struct = new Struct(null, fields, true);
            
            // 检查是否有字段名
            boolean hasFieldName = ctx.fieldName() != null;
            String fieldName = hasFieldName ? ctx.fieldName().getText() : null;
            
            if (!hasFieldName) {
                // 真正的匿名 struct（无字段名）：将字段直接展开到父级
                for (Field f : fields) {
                    addField(f.name(), f.type(), f.bitWidth());
                }
            } else {
                // 命名 struct：作为嵌套字段
                addNestedField(fieldName, Type.STRUCT, struct.totalBits(), struct, null);
            }
            return null;
        }
        
        // 匿名联合体: union { ... } name; 或 union { ... }; (无名称)
        if (first.equals("union") && ctx.fieldList() != null) {
            var fields = parseUnionFields(ctx.fieldList());
            var union = new Union(null, fields, true);
            
            // 检查是否有字段名
            boolean hasFieldName = ctx.fieldName() != null;
            String fieldName = hasFieldName ? ctx.fieldName().getText() : null;
            
            if (!hasFieldName) {
                // 真正的匿名 union（无字段名）：将字段直接展开到父级，所有字段共享相同偏移量
                int maxBits = fields.stream().mapToInt(Field::bitWidth).max().orElse(0);
                int currentUnionId = anonymousUnionCounter++;
                for (Field f : fields) {
                    // 添加字段，但标记为匿名 union 的成员
                    addFieldFromAnonymousUnionWithId(f.name(), f.type(), f.bitWidth(), maxBits, currentUnionId);
                }
            } else {
                // 命名 union：作为嵌套字段
                addNestedField(fieldName, Type.UNION, union.totalBits(), null, union);
            }
            return null;
        }
        
        // 引用结构体: struct StructName name;
        if (first.equals("struct") && ctx.Identifier() != null) {
            String structName = ctx.Identifier().getText();
            String fieldName = ctx.fieldName().getText();
            
            var ref = result.getStructByName(structName);
            if (ref != null) {
                // 检查交叉引用：如果该结构体正在解析中
                if (currentlyParsing.contains(structName)) {
                    addError(ctx, "Circular reference detected: " + structName);
                    addField(fieldName, Type.STRUCT, 0);
                    return null;
                }
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
                // 检查交叉引用：如果该联合体正在解析中
                if (currentlyParsing.contains(unionName)) {
                    addError(ctx, "Circular reference detected: " + unionName);
                    addField(fieldName, Type.UNION, 0);
                    return null;
                }
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
                
                // 检查交叉引用：如果该类型已声明且正在解析中
                if (declaredNames.contains(typeName) && currentlyParsing.contains(typeName)) {
                    addError(ctx, "Circular reference detected: " + typeName);
                    addField(fieldName, Type.CUSTOM, 0);
                    return null;
                }
                
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
                
                // 如果类型已声明但未找到，说明是前向引用（不允许）
                if (declaredNames.contains(typeName)) {
                    addError(ctx, "Forward reference not allowed: " + typeName);
                    addField(fieldName, Type.CUSTOM, 0);
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
    
    /**
     * 字段组，用于表示来自同一个匿名 union 的字段
     */
    private record FieldGroup(List<Field> fields, int unionWidth, boolean isAnonymousUnion) {
        static FieldGroup single(Field field) {
            return new FieldGroup(List.of(field), field.bitWidth(), false);
        }
        
        static FieldGroup anonymousUnion(List<Field> fields, int unionWidth) {
            return new FieldGroup(fields, unionWidth, true);
        }
    }
    
    // 用于跟踪当前正在收集的匿名 union 字段
    private int anonymousUnionCounter = 0;
    
    private List<Field> parseFields(StructParserParser.FieldListContext ctx) {
        if (ctx == null) return List.of();
        
        stack.push(new Context(new ArrayList<>()));
        ctx.field().forEach(this::visit);
        var context = stack.pop();
        
        // 将字段分组（识别匿名 union 字段组）
        List<FieldGroup> groups = groupFields(context.fields);
        
        // 计算偏移量
        var result = new ArrayList<Field>();
        int offset = 0;
        for (FieldGroup group : groups) {
            if (group.isAnonymousUnion()) {
                // 匿名 union：所有字段共享相同的 offset
                for (Field f : group.fields()) {
                    Field updatedField = setFieldOffset(f, offset);
                    result.add(updatedField);
                }
                offset += group.unionWidth();
            } else {
                // 普通字段或命名嵌套结构
                for (Field f : group.fields()) {
                    Field updatedField = setFieldOffset(f, offset);
                    result.add(updatedField);
                    offset += f.bitWidth();
                }
            }
        }
        return result;
    }
    
    /**
     * 将字段列表分组，识别来自同一个匿名 union 的字段
     */
    private List<FieldGroup> groupFields(List<Field> fields) {
        List<FieldGroup> groups = new ArrayList<>();
        int i = 0;
        
        while (i < fields.size()) {
            Field current = fields.get(i);
            
            // 检查是否是匿名 union 字段的标记
            String unionId = getAnonymousUnionId(current);
            if (unionId != null) {
                // 收集所有属于这个匿名 union 的字段
                List<Field> unionFields = new ArrayList<>();
                int maxBits = 0;
                
                while (i < fields.size()) {
                    Field f = fields.get(i);
                    String fid = getAnonymousUnionId(f);
                    // 如果字段不属于同一个 union，停止收集
                    if (fid == null || !fid.equals(unionId)) {
                        break;
                    }
                    // 清理字段名称，去除标记前缀
                    String cleanName = stripAnonymousUnionPrefix(f.name());
                    Field cleanField = new Field(cleanName, f.type(), f.bitWidth(), f.bitOffset(), f.nestedStruct(), f.nestedUnion());
                    unionFields.add(cleanField);
                    maxBits = Math.max(maxBits, f.bitWidth());
                    i++;
                }
                
                groups.add(FieldGroup.anonymousUnion(unionFields, maxBits));
            } else {
                groups.add(FieldGroup.single(current));
                i++;
            }
        }
        
        return groups;
    }
    
    /**
     * 获取字段的匿名 union ID，如果不是匿名 union 成员则返回 null
     */
    private String getAnonymousUnionId(Field field) {
        if (field.name() == null) {
            return null;
        }
        String name = field.name();
        if (name.startsWith("__anon_union_") && name.contains("__member__")) {
            int start = "__anon_union_".length();
            int end = name.indexOf("__member__");
            return name.substring(start, end);
        }
        return null;
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
    
    /**
     * 检查字段是否是匿名 union 的标记
     */
    private boolean isAnonymousUnionMarker(Field field) {
        return field.name() != null && field.name().startsWith("__anon_union__");
    }
    
    /**
     * 检查字段是否是匿名 union 的成员
     */
    private boolean isAnonymousUnionMember(Field field) {
        return field.name() != null && field.name().startsWith("__anon_union_member__");
    }
    
    private List<Field> parseUnionFields(StructParserParser.FieldListContext ctx) {
        if (ctx == null) return List.of();
        
        stack.push(new Context(new ArrayList<>()));
        ctx.field().forEach(this::visit);
        var context = stack.pop();
        
        // Union 内的字段在创建时 offset 为 0，后续会在父级结构中更新
        // 注意：匿名 union/struct 的字段已经被展开，所以这里只处理真正的 union 字段
        return context.fields.stream()
            .map(f -> new Field(f.name(), f.type(), f.bitWidth(), 0, f.nestedStruct(), f.nestedUnion()))
            .toList();
    }
    
    private void addField(String name, Type type, int width) {
        if (!stack.isEmpty()) {
            stack.peek().fields.add(Field.of(name, type, width));
        }
    }
    
    /**
     * 添加来自匿名 union 的字段
     * @param name 字段名
     * @param type 字段类型
     * @param width 字段位宽
     * @param unionWidth 匿名 union 的总宽度（最大字段宽度）
     */
    private void addFieldFromAnonymousUnion(String name, Type type, int width, int unionWidth) {
        if (!stack.isEmpty()) {
            Context ctx = stack.peek();
            // 使用特殊前缀标记这是匿名 union 的成员，包含 union ID
            String markerName = "__anon_union_" + anonymousUnionCounter + "__member__" + name;
            Field field = new Field(markerName, type, width, 0, null, null);
            ctx.fields.add(field);
        }
    }
    
    /**
     * 添加来自匿名 union 的字段（带指定 ID）
     */
    private void addFieldFromAnonymousUnionWithId(String name, Type type, int width, int unionWidth, int unionId) {
        if (!stack.isEmpty()) {
            Context ctx = stack.peek();
            String markerName = "__anon_union_" + unionId + "__member__" + name;
            Field field = new Field(markerName, type, width, 0, null, null);
            ctx.fields.add(field);
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
