grammar StructParser;

// ==================== Parser Rules ====================

program
    : statement* EOF
    ;

// 顶层语句：可以是声明，也可以是其他任意内容（会被跳过）
statement
    : declaration
    | otherStatement  // 忽略其他语句
    ;

// 其他语句：匹配任何非声明的语句并跳过
otherStatement
    : ~('struct' | 'union' | 'typedef')+  // 匹配任何不是 struct/union/typedef 开头的 token
    ;

declaration
    : structDeclaration
    | unionDeclaration
    | typedefDeclaration
    ;

structDeclaration
    : 'struct' Identifier? '{' fieldList '}' ';'
    ;

unionDeclaration
    : 'union' Identifier? '{' fieldList '}' ';'
    ;

typedefDeclaration
    : 'typedef' typeDefinition Identifier ';'
    ;

typeDefinition
    : 'struct' '{' fieldList '}'
    | 'union' '{' fieldList '}'
    | typeSpecifier
    ;

fieldList
    : field*
    ;

field
    : typeSpecifier fieldName ';'                                    // 基础类型字段
    | 'struct' '{' fieldList '}' fieldName ';'                       // 匿名结构体带名称
    | 'union' '{' fieldList '}' fieldName ';'                        // 匿名联合体带名称
    | 'struct' '{' fieldList '}' ';'                                 // 匿名结构体（无名称，字段展开）
    | 'union' '{' fieldList '}' ';'                                  // 匿名联合体（无名称，字段展开）
    | 'struct' Identifier fieldName ';'                              // 引用已定义的结构体（标准C语法）
    | 'union' Identifier fieldName ';'                               // 引用已定义的联合体（标准C语法）
    | Identifier fieldName ';'                                       // DSL语法：直接使用类型名称引用结构体/联合体
    | structDeclaration                                              // 嵌套结构体定义
    | unionDeclaration                                               // 嵌套联合体定义
    | otherField                                                     // 忽略其他无法识别的字段
    ;

// 其他字段：匹配任何无法识别的字段并跳过
otherField
    : ~('struct' | 'union' | 'uint' | Identifier)+ ';'
    ;

typeSpecifier
    : 'uint' IntegerLiteral      // uint1 ~ uint32
    | Identifier                 // typedef 定义的类型
    ;

fieldName
    : Identifier
    ;

// ==================== Lexer Rules ====================

Identifier
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

IntegerLiteral
    : [0-9]+
    ;

// Comments
LineComment
    : '//' ~[\r\n]* -> skip
    ;

BlockComment
    : '/*' .*? '*/' -> skip
    ;

// Whitespace
Whitespace
    : [ \t\r\n]+ -> skip
    ;

// Preprocessor directives (skip)
PreprocessorDirective
    : '#' ~[\r\n]* -> skip
    ;

// Catch-all for any other characters (skip to avoid errors)
AnyOther
    : . -> skip
    ;
