package thb.mdsd.spring.extractor.container;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import lombok.NonNull;

import java.util.List;

public record JavaMethodAnnotationContainer(@NonNull MethodDeclaration declaration, @NonNull List<AnnotationExpr> annotations) { }
