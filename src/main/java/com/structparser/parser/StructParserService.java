package com.structparser.parser;

import com.structparser.StructParserLexer;
import com.structparser.StructParserParser;
import com.structparser.model.ParseResult;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构体解析服务入口类 - 支持 GCC 预处理和自定义 #include 处理
 */
public class StructParserService {
    
    private static final Logger logger = LoggerFactory.getLogger(StructParserService.class);
    
    private final HeaderFileLoader fileLoader;
    private final GccPreprocessor gccPreprocessor;
    private boolean useGccPreprocessing = true; // 默认启用
    
    public StructParserService() {
        this.fileLoader = new HeaderFileLoader();
        this.gccPreprocessor = new GccPreprocessor();
    }
    
    /**
     * 从编译配置文件加载预处理命令
     */
    public StructParserService loadCompileConfig(Path configFile) throws IOException {
        logger.info("Loading compile config: {}", configFile.toAbsolutePath());
        gccPreprocessor.loadCompileConfig(configFile);
        return this;
    }
    
    /**
     * 禁用 GCC 预处理，使用自定义 #include 处理
     */
    public StructParserService disableGccPreprocessing() {
        this.useGccPreprocessing = false;
        return this;
    }
    
    public ParseResult parse(String input) {
        return doParse(CharStreams.fromString(input));
    }
    
    /**
     * 从文件解析，自动选择预处理方式
     */
    public ParseResult parseFile(Path filePath) throws IOException {
        logger.info("Parsing file: {}", filePath.toAbsolutePath());
        String content;
        List<String> preprocessErrors = new ArrayList<>();
        
        if (useGccPreprocessing) {
            // 使用 GCC 预处理
            if (!GccPreprocessor.isGccAvailable()) {
                logger.error("GCC preprocessing enabled but GCC is not available");
                return ParseResult.empty().withError(
                    "GCC preprocessing enabled but GCC is not available. " +
                    "Please install GCC or disable GCC preprocessing."
                );
            }
            
            var result = gccPreprocessor.preprocess(filePath);
            if (result.hasErrors()) {
                logger.error("GCC preprocessing failed for file: {}", filePath.getFileName());
                var errorResult = ParseResult.empty();
                for (String error : result.errors()) {
                    errorResult = errorResult.withError(error);
                }
                return errorResult;
            }
            content = result.content();
        } else {
            // 使用自定义 #include 处理
            logger.debug("Using custom #include processing");
            var loadResult = fileLoader.load(filePath);
            if (loadResult.hasErrors()) {
                logger.error("File loading failed for: {}", filePath.getFileName());
                var result = ParseResult.empty();
                for (String error : loadResult.errors()) {
                    result = result.withError(error);
                }
                return result;
            }
            content = loadResult.content();
        }
        
        logger.debug("Starting ANTLR parsing...");
        return parse(content);
    }
    
    public ParseResult parseFile(String filePath) throws IOException {
        return parseFile(Paths.get(filePath));
    }
    
    public ParseResult parse(InputStream inputStream) throws IOException {
        return doParse(CharStreams.fromStream(inputStream, StandardCharsets.UTF_8));
    }
    
    /**
     * 使用 GCC 预处理后再解析
     */
    public ParseResult parseFileWithGcc(Path filePath) throws IOException {
        boolean original = useGccPreprocessing;
        useGccPreprocessing = true;
        try {
            return parseFile(filePath);
        } finally {
            useGccPreprocessing = original;
        }
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
            logger.error("Parse error: {}", error);
            result = result.withError(error);
        }
        
        if (!result.hasErrors()) {
            logger.info("Parsing completed successfully. Found {} structs, {} unions", 
                result.structs().size(), result.unions().size());
        } else {
            logger.warn("Parsing completed with {} errors", result.errors().size());
        }
        
        return result;
    }
    
    public HeaderFileLoader getFileLoader() {
        return fileLoader;
    }
    
    public GccPreprocessor getGccPreprocessor() {
        return gccPreprocessor;
    }
    
    /**
     * 检查 GCC 是否可用
     */
    public static boolean isGccAvailable() {
        return GccPreprocessor.isGccAvailable();
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
