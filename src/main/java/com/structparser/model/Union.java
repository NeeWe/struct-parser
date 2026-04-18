package com.structparser.model;

import java.util.List;
import java.util.Objects;

/**
 * 联合体模型 - 使用 JDK 16+ Record 特性
 */
public record Union(String name, List<Field> fields, boolean anonymous) {
    
    public Union {
        fields = List.copyOf(Objects.requireNonNullElse(fields, List.of()));
    }
    
    /** 获取总位数（最大字段位宽） */
    public int totalBits() {
        return fields.stream().mapToInt(Field::bitWidth).max().orElse(0);
    }
}
