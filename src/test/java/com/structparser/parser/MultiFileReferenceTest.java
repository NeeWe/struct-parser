package com.structparser.parser;

import com.structparser.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多文件引用场景测试 - 验证 b.h 引用 a.h 中的 struct/union
 */
public class MultiFileReferenceTest {
    
    private static final Path INCLUDE_DIR = Paths.get("src/main/resources/include");
    
    private StructParserService parser;
    
    @BeforeEach
    public void setUp() {
        parser = new StructParserService()
            .disableGccPreprocessing(); // 使用自定义 #include 处理而不是 GCC
        // 添加搜索路径以便找到被引用的头文件 - 使用 HeaderFileLoader
        parser.getFileLoader().addSearchPath(INCLUDE_DIR);
    }
    
    @Test
    @DisplayName("测试 device_types.h 引用 base_types.h")
    public void testDeviceTypesIncludeBaseTypes() throws IOException {
        Path deviceFile = INCLUDE_DIR.resolve("device_types.h");
        ParseResult result = parser.parseFile(deviceFile);
        
        // 不应该有错误
        assertFalse(result.hasErrors(), "Should parse without errors: " + result.errors());
        
        // 验证基础类型被正确解析（来自 base_types.h）
        assertNotNull(result.getStructByName("Status"), "Status should be parsed from base_types.h");
        assertNotNull(result.getUnionByName("ConfigValue"), "ConfigValue should be parsed from base_types.h");
        assertNotNull(result.getStructByName("Version"), "Version should be parsed from base_types.h");
        
        // 验证设备类型被正确解析（来自 device_types.h）
        assertNotNull(result.getStructByName("DeviceInfo"), "DeviceInfo should be parsed");
        assertNotNull(result.getStructByName("DeviceConfig"), "DeviceConfig should be parsed");
        assertNotNull(result.getStructByName("DataPacket"), "DataPacket should be parsed");
    }
    
    @Test
    @DisplayName("验证 DeviceInfo 结构体嵌套引用")
    public void testDeviceInfoNestedReferences() throws IOException {
        Path deviceFile = INCLUDE_DIR.resolve("device_types.h");
        ParseResult result = parser.parseFile(deviceFile);
        
        Struct deviceInfo = result.getStructByName("DeviceInfo");
        assertNotNull(deviceInfo);
        
        // 验证字段数量：version, status, device_id
        assertEquals(3, deviceInfo.fields().size(), "DeviceInfo should have 3 fields");
        
        // 验证 version 字段（引用 Version 结构体）
        Field versionField = deviceInfo.fields().get(0);
        assertEquals("version", versionField.name());
        assertEquals(Type.STRUCT, versionField.type());
        assertNotNull(versionField.nestedStruct(), "version should have nested struct");
        assertEquals(32, versionField.bitWidth(), "Version should be 32 bits (8+8+16)");
        
        // 验证 status 字段（引用 Status 结构体）
        Field statusField = deviceInfo.fields().get(1);
        assertEquals("status", statusField.name());
        assertEquals(Type.STRUCT, statusField.type());
        assertNotNull(statusField.nestedStruct(), "status should have nested struct");
        assertEquals(16, statusField.bitWidth(), "Status should be 16 bits (8+8)");
        
        // 验证 device_id 字段
        Field deviceIdField = deviceInfo.fields().get(2);
        assertEquals("device_id", deviceIdField.name());
        assertEquals(Type.UINT32, deviceIdField.type());
        assertEquals(32, deviceIdField.bitWidth());
    }
    
    @Test
    @DisplayName("验证 DeviceConfig 结构体嵌套联合体引用")
    public void testDeviceConfigNestedUnion() throws IOException {
        Path deviceFile = INCLUDE_DIR.resolve("device_types.h");
        ParseResult result = parser.parseFile(deviceFile);
        
        Struct deviceConfig = result.getStructByName("DeviceConfig");
        assertNotNull(deviceConfig);
        
        // 验证字段数量：mode, config, timeout
        assertEquals(3, deviceConfig.fields().size(), "DeviceConfig should have 3 fields");
        
        // 验证 config 字段（引用 ConfigValue 联合体）
        Field configField = deviceConfig.fields().get(1);
        assertEquals("config", configField.name());
        assertEquals(Type.UNION, configField.type());
        assertNotNull(configField.nestedUnion(), "config should have nested union");
        assertEquals(32, configField.bitWidth(), "ConfigValue should be 32 bits");
        
        // 验证嵌套联合体的字段
        Union nestedUnion = configField.nestedUnion();
        assertEquals(2, nestedUnion.fields().size(), "ConfigValue should have 2 fields");
        
        Field rawField = nestedUnion.fields().get(0);
        assertEquals("raw", rawField.name());
        assertEquals(Type.UINT32, rawField.type());
        
        Field partsField = nestedUnion.fields().get(1);
        assertEquals("parts", partsField.name());
        assertNotNull(partsField.nestedStruct(), "parts should have nested struct");
    }
    
    @Test
    @DisplayName("验证 DataPacket 复杂嵌套结构")
    public void testDataPacketComplexNesting() throws IOException {
        Path deviceFile = INCLUDE_DIR.resolve("device_types.h");
        ParseResult result = parser.parseFile(deviceFile);
        
        Struct dataPacket = result.getStructByName("DataPacket");
        assertNotNull(dataPacket);
        
        // 验证字段数量：header_version, length, integrity, packet_status
        assertEquals(4, dataPacket.fields().size(), "DataPacket should have 4 fields");
        
        // 验证 header_version 字段
        Field versionField = dataPacket.fields().get(0);
        assertEquals("header_version", versionField.name());
        assertNotNull(versionField.nestedStruct());
        
        // 验证 length 字段
        Field lengthField = dataPacket.fields().get(1);
        assertEquals("length", lengthField.name());
        assertEquals(Type.UINT16, lengthField.type());
        
        // 验证 integrity 字段（匿名联合体）
        Field integrityField = dataPacket.fields().get(2);
        assertEquals("integrity", integrityField.name());
        assertNotNull(integrityField.nestedUnion(), "integrity should have nested union");
        
        Union integrityUnion = integrityField.nestedUnion();
        assertEquals(2, integrityUnion.fields().size(), "integrity union should have 2 fields");
        
        // 验证 checksum 字段
        Field checksumField = integrityUnion.fields().get(0);
        assertEquals("checksum", checksumField.name());
        assertEquals(Type.UINT32, checksumField.type());
        
        // 验证 crc_parts 字段（嵌套结构体）
        Field crcPartsField = integrityUnion.fields().get(1);
        assertEquals("crc_parts", crcPartsField.name());
        assertNotNull(crcPartsField.nestedStruct(), "crc_parts should have nested struct");
        
        // 验证 packet_status 字段
        Field statusField = dataPacket.fields().get(3);
        assertEquals("packet_status", statusField.name());
        assertNotNull(statusField.nestedStruct());
    }
    
    @Test
    @DisplayName("验证跨文件引用的偏移量计算")
    public void testCrossFileOffsetCalculation() throws IOException {
        Path deviceFile = INCLUDE_DIR.resolve("device_types.h");
        ParseResult result = parser.parseFile(deviceFile);
        
        Struct deviceInfo = result.getStructByName("DeviceInfo");
        assertNotNull(deviceInfo);
        
        // 验证偏移量计算正确
        Field versionField = deviceInfo.fields().get(0);
        assertEquals(0, versionField.bitOffset(), "version offset should be 0");
        
        Field statusField = deviceInfo.fields().get(1);
        assertEquals(32, statusField.bitOffset(), "status offset should be 32 (after version)");
        
        Field deviceIdField = deviceInfo.fields().get(2);
        assertEquals(48, deviceIdField.bitOffset(), "device_id offset should be 48 (after status)");
        
        // 验证总大小
        assertEquals(80, deviceInfo.totalBits(), "DeviceInfo total should be 80 bits");
    }
    
    @Test
    @DisplayName("验证单独解析 base_types.h")
    public void testParseBaseTypesAlone() throws IOException {
        Path baseFile = INCLUDE_DIR.resolve("base_types.h");
        ParseResult result = parser.parseFile(baseFile);
        
        assertFalse(result.hasErrors(), "Should parse base_types.h without errors");
        
        // 验证所有基础类型都被解析
        assertNotNull(result.getStructByName("Status"));
        assertNotNull(result.getUnionByName("ConfigValue"));
        assertNotNull(result.getStructByName("Version"));
        
        // 验证没有其他类型
        assertEquals(2, result.structs().size(), "Should have 2 structs");
        assertEquals(1, result.unions().size(), "Should have 1 union");
    }
    
    @Test
    @DisplayName("验证嵌套结构体的递归字段")
    public void testRecursiveNestedFields() throws IOException {
        Path deviceFile = INCLUDE_DIR.resolve("device_types.h");
        ParseResult result = parser.parseFile(deviceFile);
        
        Struct deviceInfo = result.getStructByName("DeviceInfo");
        Field versionField = deviceInfo.fields().get(0);
        Struct nestedVersion = versionField.nestedStruct();
        
        // 验证嵌套的 Version 结构体有正确的字段
        assertNotNull(nestedVersion);
        assertEquals(3, nestedVersion.fields().size(), "Version should have 3 fields");
        
        Field majorField = nestedVersion.fields().get(0);
        assertEquals("major", majorField.name());
        assertEquals(Type.UINT8, majorField.type());
        assertEquals(0, majorField.bitOffset());
        
        Field minorField = nestedVersion.fields().get(1);
        assertEquals("minor", minorField.name());
        assertEquals(Type.UINT8, minorField.type());
        assertEquals(8, minorField.bitOffset());
        
        Field patchField = nestedVersion.fields().get(2);
        assertEquals("patch", patchField.name());
        assertEquals(Type.UINT16, patchField.type());
        assertEquals(16, patchField.bitOffset());
    }
}
