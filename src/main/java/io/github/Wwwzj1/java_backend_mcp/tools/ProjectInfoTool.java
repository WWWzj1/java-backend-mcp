package io.github.Wwwzj1.java_backend_mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProjectInfoTool {

    @McpTool(
            name = "get_project_info",
            description = "分析指定目录下java maven基本信息"
    )
    public Map<String, Object> getProjectInfo(
            @McpToolParam(
                    description = "Java 项目的绝对路径",
                    required = true)
            String path
    ) throws IOException {
        //1.获取项目根目录
        Path inputPath = Path.of(path);
        if (!inputPath.isAbsolute()) {
            throw new IllegalArgumentException("必须提供项目的绝对路径");
        }
        if (!Files.isDirectory(inputPath)) {
            throw new IllegalArgumentException("指定目录不存在");
        }

        //2.判断是否是maven项目
        Path pomPath = inputPath.resolve("pom.xml");
        boolean isMaven = Files.isRegularFile(pomPath);
        String pomContent = isMaven
                ? Files.readString(pomPath)
                : "";
        //3.获取Java版本
        String javaVersion = "Java版本未指定";
        Matcher matcher = Pattern
                .compile("<java\\.version>\\s*([^<]+)")
                .matcher(pomContent);

        if (matcher.find()) {
            javaVersion = matcher.group(1).trim();
        }
        // 4. 判断 Spring Boot
        String framework = pomContent.contains("spring-boot")
                ? "Spring Boot"
                : "未知";

        // 5. 判断 Java 源码目录是否存在
        boolean hasJavaSource = Files.isDirectory(
                inputPath.resolve("src/main/java")
        );

        // 6. 组装分析结果
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("projectName", inputPath.getFileName().toString());
        result.put("projectPath", inputPath.toString());
        result.put("buildTool", isMaven ? "Maven" : "Unknown");
        result.put("language", "Java");
        result.put("javaVersion", javaVersion);
        result.put("framework", framework);
        result.put("hasJavaSource", hasJavaSource);

        return result;
    }
}
