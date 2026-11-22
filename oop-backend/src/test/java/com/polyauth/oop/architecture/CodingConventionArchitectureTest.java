package com.polyauth.oop.architecture;

import com.polyauth.oop.interfaces.controller.LogController;
import com.polyauth.oop.interfaces.controller.UserController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 OOP 分层依赖、注解格式和分页响应类型等编码约束。
 */
class CodingConventionArchitectureTest {

    private static final Pattern MULTIPLE_ANNOTATIONS =
            Pattern.compile("@[A-Za-z_][\\w.]*(?:\\([^\\r\\n]*?\\))?\\s+@[A-Za-z_]");

    private static final Pattern ANNOTATION_WITH_TYPE =
            Pattern.compile(
                    "^\\s*@[A-Za-z_][^\\r\\n]*\\s+"
                            + "(?:String|Long|Integer|int|boolean|Boolean|List|Set|Map|"
                            + "LocalDateTime|RequestMethod|Object)\\b");

    /**
     * 分页接口必须返回专用 VO，不能使用无类型的 Map 协议。
     */
    @Test
    @DisplayName("分页接口返回专用 PageVO")
    void pageEndpointsUseDedicatedVos() {
        assertThat(genericReturnType(UserController.class, "list"))
                .isEqualTo("UserPageVO");
        assertThat(genericReturnType(LogController.class, "list"))
                .isEqualTo("LogPageVO");
    }

    /**
     * OOP 应用层和领域层不得依赖持久化技术或基础设施实现。
     */
    @Test
    @DisplayName("OOP application/domain 不依赖持久化实现")
    void applicationAndDomainDoNotDependOnPersistence() throws IOException {
        List<String> violations = javaFiles(
                backendRoot().resolve("src/main/java/com/polyauth/oop/application"),
                backendRoot().resolve("src/main/java/com/polyauth/oop/domain"))
                .flatMap(path -> readLines(path).stream()
                        .filter(line -> line.contains("jakarta.persistence")
                                || line.contains("JpaRepository")
                                || line.contains("org.springframework.data.domain.Page")
                                || line.contains("com.polyauth.oop.infrastructure"))
                        .map(line -> path + ": " + line))
                .toList();

        assertThat(violations)
                .as("application/domain 中的持久化依赖")
                .isEmpty();
    }

    /**
     * Java 注解必须独占一行，避免参数签名被压缩而难以审查。
     */
    @Test
    @DisplayName("Java 注解独占一行")
    void annotationsUseTheirOwnLines() throws IOException {
        List<String> violations = javaFiles(
                backendRoot().resolve("src/main/java"),
                backendRoot().resolve("src/test/java"))
                .flatMap(path -> {
                    List<String> lines = readLines(path);
                    return java.util.stream.IntStream.range(0, lines.size())
                            .filter(index -> MULTIPLE_ANNOTATIONS.matcher(lines.get(index)).find()
                                    || ANNOTATION_WITH_TYPE.matcher(lines.get(index)).find())
                            .mapToObj(index -> path + ":" + (index + 1) + ": " + lines.get(index));
                })
                .toList();

        assertThat(violations)
                .as("未独占一行的 Java 注解")
                .isEmpty();
    }

    private String genericReturnType(Class<?> type, String methodName) {
        Method method = Stream.of(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        ParameterizedType resultType = (ParameterizedType) method.getGenericReturnType();
        java.lang.reflect.Type pageType = resultType.getActualTypeArguments()[0];
        if (pageType instanceof ParameterizedType parameterizedType) {
            return ((Class<?>) parameterizedType.getRawType()).getSimpleName();
        }
        return ((Class<?>) pageType).getSimpleName();
    }

    private Path backendRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(workingDirectory.resolve("src/main/java"))) {
            return workingDirectory;
        }
        return workingDirectory.resolve("oop-backend");
    }

    private Stream<Path> javaFiles(Path... roots) throws IOException {
        List<Path> files = new java.util.ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files.stream();
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Java 源文件: " + path, exception);
        }
    }
}
