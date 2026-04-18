package com.structparser.model;

/**
 * 字段模型 - 使用 JDK 16+ Record 特性
 */
public record Field(String name, Type type, int bitWidth, int bitOffset) {
    
    /** 创建字段（偏移量为0） */
    public static Field of(String name, Type type, int bitWidth) {
        return new Field(name, type, bitWidth, 0);
    }
}
