package com.structparser.model;

/**
 * 字段模型 - 使用 JDK 16+ Record 特性
 */
public record Field(String name, Type type, int bitWidth, int bitOffset, Struct nestedStruct, Union nestedUnion) {
    
    /** 创建字段（偏移量为0） */
    public static Field of(String name, Type type, int bitWidth) {
        return new Field(name, type, bitWidth, 0, null, null);
    }
    
    /** 创建带有嵌套 struct 的字段 */
    public static Field withNestedStruct(String name, int bitWidth, int bitOffset, Struct nestedStruct) {
        return new Field(name, Type.STRUCT, bitWidth, bitOffset, nestedStruct, null);
    }
    
    /** 创建带有嵌套 union 的字段 */
    public static Field withNestedUnion(String name, int bitWidth, int bitOffset, Union nestedUnion) {
        return new Field(name, Type.UNION, bitWidth, bitOffset, null, nestedUnion);
    }
}
