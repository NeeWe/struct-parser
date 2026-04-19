grammar StructParser;

// ==================== Parser Rules ====================

program
    : declaration* EOF
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
    | 'struct' Identifier fieldName ';'                              // 引用已定义的结构体（标准C语法）
    | 'union' Identifier fieldName ';'                               // 引用已定义的联合体（标准C语法）
    | Identifier fieldName ';'                                       // DSL语法：直接使用类型名称引用结构体/联合体
    | structDeclaration                                              // 嵌套结构体定义
    | unionDeclaration                                               // 嵌套联合体定义
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

// Preprocessor directives (skip for now)
PreprocessorDirective
    : '#' ~[\r\n]* -> skip
    ;
