grammar StructParser;

// ==================== Parser Rules ====================

// 程序入口：匹配任意内容，只提取 struct/union/typedef 定义
program
    : item* EOF
    ;

// 顶层项目：声明或任意其他内容
item
    : declaration
    | otherContent  // 语法岛：忽略不识别的内容
    ;

// 其他内容：匹配任意 token 并跳过
otherContent
    : ~('struct' | 'union' | 'typedef')+
    ;

// 声明语句
declaration
    : structDeclaration
    | unionDeclaration
    | typedefDeclaration
    ;

// 结构体定义：struct Name? { fields } ;
structDeclaration
    : 'struct' Identifier? '{' fieldList '}' ';'
    ;

// 联合体定义：union Name? { fields } ;
unionDeclaration
    : 'union' Identifier? '{' fieldList '}' ';'
    ;

// typedef 定义
typedefDeclaration
    : 'typedef' typeDefinition Identifier ';'
    ;

// typedef 的类型定义
typeDefinition
    : 'struct' '{' fieldList '}'
    | 'union' '{' fieldList '}'
    | typeSpecifier
    ;

// 字段列表
fieldList
    : field*
    ;

// 字段定义（多种情况）
field
    // 基础类型：uintN name;
    : typeSpecifier fieldName ';'
    
    // 匿名结构体/联合体：有字段名时作为嵌套，无字段名时展开
    | 'struct' '{' fieldList '}' fieldName? ';'
    | 'union' '{' fieldList '}' fieldName? ';'
    
    // 引用已定义的类型：StructName name; 或 UnionName name;
    | Identifier fieldName ';'
    
    // 标准 C 语法：struct/union Name name;
    | 'struct' Identifier fieldName ';'
    | 'union' Identifier fieldName ';'
    
    // 忽略其他无法识别的字段
    | otherField
    ;

// 其他字段：匹配到分号的内容并跳过
otherField
    : ~('struct' | 'union' | 'typedef' | 'uint' | Identifier)+ ';'
    ;

// 类型说明符
typeSpecifier
    : 'uint' IntegerLiteral      // uint1 ~ uint32
    | Identifier                 // typedef 或自定义类型
    ;

// 字段名
fieldName
    : Identifier
    ;

// ==================== Lexer Rules ====================

// 标识符
Identifier
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

// 整数常量
IntegerLiteral
    : [0-9]+
    ;

// 注释（跳过）
LineComment
    : '//' ~[\r\n]* -> skip
    ;

BlockComment
    : '/*' .*? '*/' -> skip
    ;

// 空白字符（跳过）
Whitespace
    : [ \t\r\n]+ -> skip
    ;

// 预处理指令（跳过）
PreprocessorDirective
    : '#' ~[\r\n]* -> skip
    ;

// 捕获任何其他字符（跳过，实现语法岛模式）
AnyOther
    : . -> skip
    ;
