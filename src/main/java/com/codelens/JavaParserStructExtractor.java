package com.codelens;

import com.codelens.common.normalizers.StructContext;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.nio.file.Path;

/**
 * 使用 JavaParser 提取代码结构底图数据（CLI端实现）
 * 插件端使用 PSI 实现同名功能，输出格式一致
 */
public class JavaParserStructExtractor {

    public static StructContext extract(Path filePath) throws Exception {
        StructContext context = new StructContext();

        CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());

        // 包名
        cu.getPackageDeclaration().ifPresent(pkg ->
            context.setPackageName(pkg.getNameAsString())
        );

        // 类名
        cu.findFirst(ClassOrInterfaceDeclaration.class)
            .ifPresent(cls -> context.setClassName(cls.getNameAsString()));

        // 字段
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            for (VariableDeclarator var : field.getVariables()) {
                String injection = "";
                if (field.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Autowired"))) {
                    injection = "@Autowired";
                } else if (field.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Resource"))) {
                    injection = "@Resource";
                } else if (field.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Inject"))) {
                    injection = "@Inject";
                }

                context.getFields().add(new StructContext.FieldInfo(
                    var.getNameAsString(),
                    field.getElementType().asString(),
                    var.getRange().map(r -> r.begin.line).orElse(-1),
                    injection
                ));
            }
        });

        // 方法
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String visibility = method.isPublic() ? "public" :
                              method.isPrivate() ? "private" :
                              method.isProtected() ? "protected" : "default";
            String signature = method.getNameAsString() + "(" +
                method.getParameters().stream()
                    .map(p -> p.getType().asString())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("") + ")";

            context.getMethods().add(new StructContext.MethodInfo(
                signature,
                method.getRange().map(r -> r.begin.line).orElse(-1),
                visibility
            ));
        });

        return context;
    }
}
