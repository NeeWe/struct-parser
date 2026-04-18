package com.structparser.model;

import java.util.List;
import java.util.Objects;

/**
 * 结构体模型 - 使用 JDK 16+ Record 特性
 */
public record Struct(String name, List<Field> fields, boolean anonymous) {
    
    public Struct {
        fields = List.copyOf(Objects.requireNonNullElse(fields, List.of()));
    }
    
    /** 获取总位数 */
    public int totalBits() {
        return fields.stream().mapToInt(Field::bitWidth).sum();
    }
}
