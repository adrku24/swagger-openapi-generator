package thb.mdsd.spring.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import lombok.Getter;
import lombok.NonNull;
import thb.mdsd.spring.extractor.container.JavaClassAnnotationContainer;
import thb.mdsd.spring.extractor.container.JavaMethodAnnotationContainer;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Getter
public class JavaFile {

    private static File getSourceRoot(File javaFile) {
        String path = javaFile.getAbsolutePath();
        int srcIndex = path.indexOf(File.separator + "src" + File.separator);

        if (srcIndex != -1) {
            String afterSrc = path.substring(srcIndex + 5);
            String[] parts = afterSrc.split(Pattern.quote(File.separator));

            if (parts.length >= 2) {
                String rootPath = path.substring(0, srcIndex) + File.separator + "src" + File.separator + parts[0] + File.separator + parts[1];
                return new File(rootPath);
            }
        }

        return javaFile.getParentFile();
    }

    private final File reference;
    private final String data;
    private final CompilationUnit unit;

    public JavaFile(@NonNull File reference, @NonNull String data) {
        this.reference = reference;
        this.data = data;

        try {
            final CombinedTypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(getSourceRoot(reference))
            );

            final ParserConfiguration config = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));

            final ParseResult<CompilationUnit> compilationUnit = new JavaParser(config).parse(this.reference);

            if(compilationUnit.getResult().isEmpty()) {
                throw new RuntimeException("Could not parse java file: " + reference.getAbsolutePath());
            }

            this.unit = compilationUnit.getResult().get();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public ClassOrInterfaceDeclaration getClassOrInterfaceDeclaration(@NonNull String name) {
        return this.unit.getClassByName(name).orElse(null);
    }

    public List<ClassOrInterfaceDeclaration> getClassOrInterfaceDeclarations() {
        return this.unit.findAll(ClassOrInterfaceDeclaration.class);
    }

    public List<MethodDeclaration> getMethodDeclarations() {
        return this.unit.findAll(MethodDeclaration.class);
    }

    public List<FieldDeclaration> getFieldDeclarations() {
        return this.unit.findAll(FieldDeclaration.class);
    }

    public List<JavaClassAnnotationContainer> findClassAnnotations() {
        return this.getClassOrInterfaceDeclarations().stream()
            .filter(classOrInterfaceDeclaration -> !classOrInterfaceDeclaration.getAnnotations().isEmpty())
            .map(classOrInterfaceDeclaration ->
                new JavaClassAnnotationContainer(classOrInterfaceDeclaration, classOrInterfaceDeclaration.getAnnotations())
            )
            .toList();
    }

    public List<JavaMethodAnnotationContainer> findMethodAnnotations() {
        return this.getClassOrInterfaceDeclarations().stream()
            .flatMap(classOrInterfaceDeclaration ->
                classOrInterfaceDeclaration.getMethods().stream()
                    .filter(methodDeclaration -> !methodDeclaration.getAnnotations().isEmpty())  // Only methods with annotations
                    .map(methodDeclaration ->
                        new JavaMethodAnnotationContainer(methodDeclaration, methodDeclaration.getAnnotations())
                    )
            )
            .toList();
    }

    public Optional<ImportDeclaration> findAnnotationPackage(@NonNull AnnotationExpr annotation) {
        return unit.getImports().stream().filter(importDeclaration -> importDeclaration.getNameAsString().endsWith(annotation.getNameAsString())).findFirst();
    }

    public String getPackage() {
        if(this.unit.getPackageDeclaration().isPresent()) {
            return this.unit.getPackageDeclaration().get().getNameAsString();
        } else {
            return null;
        }
    }

    public boolean hasImport(@NonNull String importName) {
        for(ImportDeclaration importDeclaration : this.unit.getImports()) {
            if(importDeclaration.getNameAsString().equals(importName)) return true;
        }
        return false;
    }
}