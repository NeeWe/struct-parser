package com.structparser.parser;

import com.structparser.model.ParseResult;
import com.structparser.model.Struct;
import com.structparser.model.Union;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试语法容错功能 - 解析器应该能够忽略无法识别的C语法，只提取struct/union定义
 */
public class SyntaxToleranceTest {
    
    private static final Path TEST_RESOURCES = Paths.get("src/test/resources/headers");
    
    @Test
    @DisplayName("解析混合C语法的头文件")
    public void testMixedSyntaxParsing() throws IOException {
        var parser = new StructParserService()
            .disableGccPreprocessing();
        
        Path file = TEST_RESOURCES.resolve("mixed_syntax.h");
        ParseResult result = parser.parseFile(file);
        
        // 不应该有解析错误（即使有很多无法识别的语法）
        assertFalse(result.hasErrors(), "Should not have parse errors: " + result.errors());
        
        // 应该成功解析出3个结构体和1个联合体
        assertEquals(2, result.structs().size(), "Should find 2 structs");
        assertEquals(1, result.unions().size(), "Should find 1 union");
        
        // 验证 ControlReg 结构体
        Struct controlReg = result.getStructByName("ControlReg");
        assertNotNull(controlReg, "ControlReg should be parsed");
        assertEquals(6, controlReg.fields().size(), "ControlReg should have 6 fields");
        assertEquals(32, controlReg.totalBits(), "ControlReg should be 32 bits");
        
        // 验证 Status 结构体
        Struct status = result.getStructByName("Status");
        assertNotNull(status, "Status should be parsed");
        assertEquals(2, status.fields().size(), "Status should have 2 fields");
        assertEquals(16, status.totalBits(), "Status should be 16 bits");
        
        // 验证 DataValue 联合体
        Union dataValue = result.getUnionByName("DataValue");
        assertNotNull(dataValue, "DataValue should be parsed");
        assertEquals(2, dataValue.fields().size(), "DataValue should have 2 fields");
        assertEquals(32, dataValue.totalBits(), "DataValue should be 32 bits");
    }
}
