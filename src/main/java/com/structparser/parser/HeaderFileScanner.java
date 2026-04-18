package com.structparser.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 头文件扫描器 - 递归扫描目录中的所有头文件
 */
public class HeaderFileScanner {
    
    private static final String[] HEADER_EXTENSIONS = {".h", ".hpp", ".hh", ".hxx"};
    
    /**
     * 扫描单个目录中的头文件（不递归子目录）
     */
    public static List<Path> scan(Path directory) throws IOException {
        var headerFiles = new ArrayList<Path>();
        
        if (!Files.exists(directory)) {
            throw new IOException("Directory does not exist: " + directory);
        }
        
        if (!Files.isDirectory(directory)) {
            throw new IOException("Path is not a directory: " + directory);
        }
        
        // 只扫描当前目录，不递归子目录
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                  .filter(HeaderFileScanner::isHeaderFile)
                  .forEach(headerFiles::add);
        }
        
        return headerFiles;
    }
    
    /**
     * 扫描多个目录中的所有头文件
     */
    public static List<Path> scan(List<Path> directories) throws IOException {
        var allHeaders = new ArrayList<Path>();
        
        for (Path dir : directories) {
            allHeaders.addAll(scan(dir));
        }
        
        return allHeaders;
    }
    
    /**
     * 检查文件是否为头文件
     */
    private static boolean isHeaderFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        for (String ext : HEADER_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取扫描统计信息
     */
    public record ScanResult(List<Path> headerFiles, int totalFiles, int totalDirectories) {
        public int headerCount() {
            return headerFiles.size();
        }
    }
}
