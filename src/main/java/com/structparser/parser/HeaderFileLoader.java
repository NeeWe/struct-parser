package com.structparser.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 头文件加载器 - 处理 #include 指令
 */
public class HeaderFileLoader {
    
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+([\"<])([^\"<>]+)[\">]");
    
    private final List<Path> searchPaths = new ArrayList<>();
    private final Set<Path> loaded = new HashSet<>();
    
    public HeaderFileLoader() {
        searchPaths.add(Paths.get("."));
    }
    
    public void addSearchPath(Path path) {
        if (Files.isDirectory(path)) searchPaths.add(path.toAbsolutePath().normalize());
    }
    
    public LoadResult load(Path file) throws IOException {
        loaded.clear();
        var content = new StringBuilder();
        var files = new ArrayList<Path>();
        var errors = new ArrayList<String>();
        
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) searchPaths.add(0, dir);
        
        loadRecursive(file.toAbsolutePath().normalize(), content, files, errors, 0);
        return new LoadResult(content.toString(), files, errors);
    }
    
    private void loadRecursive(Path file, StringBuilder out, List<Path> files, List<String> errors, int depth) {
        if (depth > 100) {
            errors.add("Include depth exceeded: " + file);
            return;
        }
        if (loaded.contains(file)) return;
        if (!Files.exists(file)) {
            errors.add("File not found: " + file);
            return;
        }
        
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            loaded.add(file);
            files.add(file);
            
            out.append("// === ").append(file.getFileName()).append(" ===\n");
            
            int lineNum = 0;
            for (String line : text.split("\n")) {
                lineNum++;
                var m = INCLUDE.matcher(line);
                if (m.find()) {
                    Path inc = find(m.group(2), m.group(1).equals("\""), file.getParent());
                    if (inc != null) {
                        loadRecursive(inc, out, files, errors, depth + 1);
                    } else {
                        errors.add(String.format("Cannot find '%s' at %s:%d", m.group(2), file, lineNum));
                    }
                } else {
                    out.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            errors.add("Error reading " + file + ": " + e.getMessage());
        }
    }
    
    private Path find(String name, boolean quoted, Path current) {
        if (quoted && current != null) {
            Path local = current.resolve(name).normalize();
            if (Files.exists(local)) return local;
        }
        for (Path p : searchPaths) {
            Path tryPath = p.resolve(name).normalize();
            if (Files.exists(tryPath)) return tryPath;
        }
        return null;
    }
    
    public record LoadResult(String content, List<Path> loadedFiles, List<String> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }
}
