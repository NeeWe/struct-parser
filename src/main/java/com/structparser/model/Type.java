package com.structparser.model;

/**
 * 类型定义枚举
 */
public enum Type {
    // 基础无符号整数类型 uint1 ~ uint32
    UINT1(1),
    UINT2(2),
    UINT3(3),
    UINT4(4),
    UINT5(5),
    UINT6(6),
    UINT7(7),
    UINT8(8),
    UINT9(9),
    UINT10(10),
    UINT11(11),
    UINT12(12),
    UINT13(13),
    UINT14(14),
    UINT15(15),
    UINT16(16),
    UINT17(17),
    UINT18(18),
    UINT19(19),
    UINT20(20),
    UINT21(21),
    UINT22(22),
    UINT23(23),
    UINT24(24),
    UINT25(25),
    UINT26(26),
    UINT27(27),
    UINT28(28),
    UINT29(29),
    UINT30(30),
    UINT31(31),
    UINT32(32),
    
    // 复合类型
    STRUCT(-1),
    UNION(-1),
    
    // 自定义类型（typedef）
    CUSTOM(-1);
    
    private final int bitWidth;
    
    Type(int bitWidth) {
        this.bitWidth = bitWidth;
    }
    
    public int getBitWidth() {
        return bitWidth;
    }
    
    /**
     * 根据位宽获取对应的 UINT 类型
     */
    public static Type fromBitWidth(int width) {
        if (width < 1 || width > 32) {
            throw new IllegalArgumentException("Bit width must be between 1 and 32, got: " + width);
        }
        return values()[width - 1]; // UINT1 在索引 0 位置
    }
    
    /**
     * 解析类型字符串，如 "uint8" -> UINT8
     */
    public static Type fromString(String typeStr) {
        if (typeStr == null || typeStr.isEmpty()) {
            throw new IllegalArgumentException("Type string cannot be null or empty");
        }
        
        String lower = typeStr.toLowerCase();
        
        // 解析 uintN 格式
        if (lower.startsWith("uint")) {
            try {
                int width = Integer.parseInt(lower.substring(4));
                return fromBitWidth(width);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid uint type: " + typeStr);
            }
        }
        
        // 其他类型
        return switch (lower) {
            case "struct" -> STRUCT;
            case "union" -> UNION;
            default -> CUSTOM;
        };
    }
    
    public boolean isPrimitive() {
        return this.bitWidth > 0;
    }
    
    public boolean isComposite() {
        return this == STRUCT || this == UNION;
    }
}
