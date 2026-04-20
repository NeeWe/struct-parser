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
        // 需要识别来自同一个匿名 union 的字段，只计算一次最大宽度
        int total = 0;
        int i = 0;
        while (i < fields.size()) {
            Field current = fields.get(i);
            
            // 检查是否有后续字段与当前字段 offset 相同（说明来自同一个 union）
            if (i + 1 < fields.size()) {
                Field next = fields.get(i + 1);
                if (current.bitOffset() == next.bitOffset()) {
                    // 找到所有具有相同 offset 的字段（来自同一个 union）
                    int maxBits = current.bitWidth();
                    int j = i + 1;
                    while (j < fields.size() && fields.get(j).bitOffset() == current.bitOffset()) {
                        maxBits = Math.max(maxBits, fields.get(j).bitWidth());
                        j++;
                    }
                    total += maxBits;
                    i = j;
                    continue;
                }
            }
            
            // 普通字段
            total += current.bitWidth();
            i++;
        }
        return total;
    }
}
