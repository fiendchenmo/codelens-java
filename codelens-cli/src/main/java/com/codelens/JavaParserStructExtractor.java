package com.codelens;

import com.codelens.common.normalizers.StructContext;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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

        // 字段 — 提取当前类的 @Autowired/@Resource/@Inject 字段
        extractInjectedFields(cu, context);

        // 继承字段 — 沿父类链向上解析，补齐父类中的注入字段
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(cls -> {
            String pkg = context.getPackageName();
            resolveParentInjectedFields(cls, cu, filePath, pkg, context);
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

    /** 提取 CompilationUnit 中所有标记了 @Autowired/@Resource/@Inject 的字段 */
    private static void extractInjectedFields(CompilationUnit cu, StructContext context) {
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            for (VariableDeclarator var : field.getVariables()) {
                String injection = getInjectionAnnotation(field);
                if (!injection.isEmpty()) {
                    context.getFields().add(new StructContext.FieldInfo(
                        var.getNameAsString(),
                        field.getElementType().asString(),
                        var.getRange().map(r -> r.begin.line).orElse(-1),
                        injection
                    ));
                }
            }
        });
    }

    /** 判断字段的注入注解类型 */
    private static String getInjectionAnnotation(FieldDeclaration field) {
        if (field.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Autowired"))) {
            return "@Autowired";
        } else if (field.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Resource"))) {
            return "@Resource";
        } else if (field.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Inject"))) {
            return "@Inject";
        }
        return "";
    }

    /** 沿父类链递归解析父类源文件中的注入字段 */
    private static void resolveParentInjectedFields(
            ClassOrInterfaceDeclaration cls, CompilationUnit cu,
            Path currentFilePath, String currentPkg, StructContext context) {

        for (ClassOrInterfaceType parentType : cls.getExtendedTypes()) {
            String parentName = parentType.getNameAsString();
            String parentPkg = findParentPackage(cu, parentName, currentPkg);
            if (parentPkg == null) continue;

            Path parentFile = resolveParentSourceFile(currentFilePath, currentPkg, parentPkg, parentName);
            if (parentFile == null || !Files.exists(parentFile)) continue;

            try {
                CompilationUnit parentCu = StaticJavaParser.parse(parentFile.toFile());
                // 提取父类中的注入字段
                extractInjectedFields(parentCu, context);
                // 递归：父类可能还有父类
                parentCu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(parentCls ->
                    resolveParentInjectedFields(parentCls, parentCu, parentFile, parentPkg, context)
                );
            } catch (Exception e) {
                // 父类文件解析失败时静默跳过
            }
        }
    }

    /** 通过 import 语句或同包约定查找父类的全限定包名 */
    private static String findParentPackage(CompilationUnit cu, String parentName, String defaultPkg) {
        // 先查显式 import
        Optional<String> matched = cu.getImports().stream()
            .filter(imp -> !imp.isAsterisk())
            .map(imp -> imp.getNameAsString())
            .filter(name -> name.endsWith("." + parentName))
            .map(name -> name.substring(0, name.lastIndexOf('.')))
            .findFirst();
        if (matched.isPresent()) return matched.get();

        // 同包（Java 同包不需要 import）
        return defaultPkg;
    }

    /** 通过源文件路径 + 包名推导父类源文件路径 */
    private static Path resolveParentSourceFile(
            Path currentFilePath, String currentPkg,
            String parentPkg, String parentName) {

        try {
            // 统一用 / 分隔符，兼容 Windows 和 Linux 路径
            String currentPkgPath = currentPkg.replace('.', '/');
            String absPath = currentFilePath.toAbsolutePath().normalize()
                .toString().replace('\\', '/');

            int pkgIndex = absPath.indexOf(currentPkgPath);
            if (pkgIndex < 0) return null;

            // 源根目录 = 文件绝对路径去掉当前包路径
            String sourceRoot = absPath.substring(0, pkgIndex);
            String parentRelPath = parentPkg.replace('.', '/') + "/" + parentName + ".java";

            return Path.of(sourceRoot + parentRelPath).normalize();
        } catch (Exception e) {
            return null;
        }
    }
}
