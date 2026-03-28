package com.agent.agentdemo.utils;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从代码文件的文本块中提取结构化元数据（类名、包名、模块名等）。
 */
@Component
public class CodeMetadataExtractor {

    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "kt", "scala",
            "py",
            "js", "ts", "jsx", "tsx",
            "go",
            "rs",
            "cpp", "cc", "cxx", "c", "h",
            "cs",
            "rb", "php", "swift"
    );

    public boolean isCodeFile(String fileType) {
        return fileType != null && CODE_EXTENSIONS.contains(fileType.toLowerCase());
    }

    /**
     * 从代码块内容提取元数据，key 会写入 PGVector 的 metadata JSON。
     */
    public Map<String, Object> extract(String content, String fileType) {
        if (fileType == null) return Map.of();
        return switch (fileType.toLowerCase()) {
            case "java", "kt", "scala" -> extractJvm(content);
            case "py"                  -> extractPython(content);
            case "js", "ts", "jsx", "tsx" -> extractJavaScript(content);
            case "go"                  -> extractGo(content);
            case "rs"                  -> extractRust(content);
            default                    -> Map.of();
        };
    }

    // ── JVM（Java / Kotlin / Scala）──
    private Map<String, Object> extractJvm(String content) {
        Map<String, Object> meta = new LinkedHashMap<>();

        find(content, "^package\\s+([\\w.]+)[;]?", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("package", v));

        find(content,
                "(?:public|protected|private|internal)?\\s*(?:abstract\\s+|data\\s+|sealed\\s+)?(?:class|interface|enum|record|object)\\s+(\\w+)",
                Pattern.MULTILINE)
                .ifPresent(v -> meta.put("class_name", v));

        return meta;
    }

    // ── Python ──
    private Map<String, Object> extractPython(String content) {
        Map<String, Object> meta = new LinkedHashMap<>();
        find(content, "^class\\s+(\\w+)", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("class_name", v));
        find(content, "^def\\s+(\\w+)", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("function_name", v));
        return meta;
    }

    // ── JavaScript / TypeScript ──
    private Map<String, Object> extractJavaScript(String content) {
        Map<String, Object> meta = new LinkedHashMap<>();
        find(content, "(?:^|\\s)class\\s+(\\w+)", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("class_name", v));
        find(content, "(?:^export\\s+)?(?:async\\s+)?function\\s+(\\w+)", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("function_name", v));
        return meta;
    }

    // ── Go ──
    private Map<String, Object> extractGo(String content) {
        Map<String, Object> meta = new LinkedHashMap<>();
        find(content, "^package\\s+(\\w+)", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("package", v));
        find(content, "^type\\s+(\\w+)\\s+struct", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("class_name", v));
        find(content, "^func\\s+(?:\\([^)]+\\)\\s+)?(\\w+)", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("function_name", v));
        return meta;
    }

    // ── Rust ──
    private Map<String, Object> extractRust(String content) {
        Map<String, Object> meta = new LinkedHashMap<>();
        find(content, "^(?:pub\\s+)?(?:struct|enum|trait|impl)\\s+(\\w+)", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("class_name", v));
        find(content, "^(?:pub\\s+)?fn\\s+(\\w+)", Pattern.MULTILINE)
                .ifPresent(v -> meta.put("function_name", v));
        return meta;
    }

    // 返回第一个捕获组
    private java.util.Optional<String> find(String text, String regex, int flags) {
        Matcher m = Pattern.compile(regex, flags).matcher(text);
        return m.find() ? java.util.Optional.of(m.group(1).trim()) : java.util.Optional.empty();
    }
}
