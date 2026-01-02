package thb.mdsd.spring.extractor.container;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import lombok.NonNull;
import thb.mdsd.spring.extractor.JavaFile;

public record SpringPathContainer(@NonNull JavaFile javaFileContainer, @NonNull MethodDeclaration methodDeclaration, @NonNull String path, @NonNull String method, int responseStatus, NodeList<Expression> consumes, NodeList<Expression> produces) { }
