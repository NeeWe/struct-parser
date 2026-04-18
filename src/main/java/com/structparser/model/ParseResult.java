package com.structparser.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 解析结果封装类 - 使用 JDK 16+ Record 特性
 */
public record ParseResult(
    List<Struct> structs,
    List<Union> unions,
    Map<String, String> typedefs,
    List<String> errors
) {
    
    public ParseResult {
        structs = List.copyOf(Objects.requireNonNullElse(structs, List.of()));
        unions = List.copyOf(Objects.requireNonNullElse(unions, List.of()));
        typedefs = Map.copyOf(Objects.requireNonNullElse(typedefs, Map.of()));
        errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
    }
    
    /** 创建空结果 */
    public static ParseResult empty() {
        return new ParseResult(List.of(), List.of(), Map.of(), List.of());
    }
    
    /** 是否有错误 */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /** 根据名称查找结构体 */
    public Struct getStructByName(String name) {
        return structs.stream()
            .filter(s -> name.equals(s.name()))
            .findFirst()
            .orElse(null);
    }
    
    /** 根据名称查找联合体 */
    public Union getUnionByName(String name) {
        return unions.stream()
            .filter(u -> name.equals(u.name()))
            .findFirst()
            .orElse(null);
    }
    
    /** 添加结构体 */
    public ParseResult withStruct(Struct struct) {
        var list = new java.util.ArrayList<>(structs);
        list.add(struct);
        return new ParseResult(list, unions, typedefs, errors);
    }
    
    /** 添加联合体 */
    public ParseResult withUnion(Union union) {
        var list = new java.util.ArrayList<>(unions);
        list.add(union);
        return new ParseResult(structs, list, typedefs, errors);
    }
    
    /** 添加类型别名 */
    public ParseResult withTypedef(String alias, String actualType) {
        var map = new java.util.HashMap<>(typedefs);
        map.put(alias, actualType);
        return new ParseResult(structs, unions, map, errors);
    }
    
    /** 添加错误 */
    public ParseResult withError(String error) {
        var list = new java.util.ArrayList<>(errors);
        list.add(error);
        return new ParseResult(structs, unions, typedefs, list);
    }
}
