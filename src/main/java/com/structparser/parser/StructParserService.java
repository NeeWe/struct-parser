package com.structparser.parser;

import com.structparser.StructParserLexer;
import com.structparser.StructParserParser;
import com.structparser.model.ParseResult;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构体解析服务入口类
 */
public class StructParserService {
    
    private final HeaderFileLoader fileLoader;
    
    public StructParserService() {
        this.fileLoader = new HeaderFileLoader();
    }
    
    public StructParserService addSearchPath(Path path) {
        fileLoader.addSearchPath(path);
        return this;
    }
    
    public StructParserService addSearchPath(String path) {
        fileLoader.addSearchPath(Paths.get(path));
        return this;
    }
    
    public ParseResult parse(String input) {
        return doParse(CharStreams.fromString(input));
    }
    
    public ParseResult parseFile(Path filePath) throws IOException {
        var loadResult = fileLoader.load(filePath);
        
        if (loadResult.hasErrors()) {
            var result = ParseResult.empty();
            for (String error : loadResult.errors()) {
                result = result.withError(error);
            }
            return result;
        }
        
        return parse(loadResult.content());
    }
    
    public ParseResult parseFile(String filePath) throws IOException {
        return parseFile(Paths.get(filePath));
    }
    
    public ParseResult parse(InputStream inputStream) throws IOException {
        return doParse(CharStreams.fromStream(inputStream, StandardCharsets.UTF_8));
    }
    
    private ParseResult doParse(CharStream charStream) {
        StructParserLexer lexer = new StructParserLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        StructParserParser parser = new StructParserParser(tokens);
        
        var errorListener = new StructErrorListener();
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        
        ParseTree tree = parser.program();
        
        StructParseVisitor visitor = new StructParseVisitor();
        visitor.visit(tree);
        
        ParseResult result = visitor.getResult();
        for (String error : errorListener.getErrors()) {
            result = result.withError(error);
        }
        
        return result;
    }
    
    public HeaderFileLoader getFileLoader() {
        return fileLoader;
    }
    
    private static class StructErrorListener extends BaseErrorListener {
        private final List<String> errors = new ArrayList<>();
        
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            errors.add(String.format("Line %d:%d - %s", line, charPositionInLine, msg));
        }
        
        public List<String> getErrors() {
            return errors;
        }
    }
}
