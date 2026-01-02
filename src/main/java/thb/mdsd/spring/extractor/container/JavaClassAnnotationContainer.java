package thb.mdsd.spring.extractor.container;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import lombok.NonNull;

import java.util.List;

public record JavaClassAnnotationContainer(@NonNull ClassOrInterfaceDeclaration declaration, @NonNull List<AnnotationExpr> annotations) { }
