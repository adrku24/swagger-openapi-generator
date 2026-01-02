package thb.mdsd.swagger;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.Type;
import lombok.Getter;
import lombok.NonNull;
import thb.mdsd.spring.SpringBootExtractor;
import thb.mdsd.spring.data.AnnotationRegistry;
import thb.mdsd.spring.data.AnnotationValueRegistry;
import thb.mdsd.spring.data.CommonAnnotationNameRegistry;
import thb.mdsd.util.PathUtils;
import thb.mdsd.util.YamlHelper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Getter
public class SwaggerAPIExport {

    private final SpringBootExtractor springBootExtractor;
    public SwaggerAPIExport(@NonNull SpringBootExtractor springBootExtractor) {
        this.springBootExtractor = springBootExtractor;

        if(!this.springBootExtractor.hasExtracted()) {
            this.springBootExtractor.startExtracting();
        }
    }

    private void checkSwaggerEnabled() {
        if(!this.springBootExtractor.isSwaggerEnabled()) {
            throw new RuntimeException("The author of the given project does not have Swagger enabled.");
        }
    }

    public void export(@NonNull File file) throws IOException {
        if(!PathUtils.isPath(file.getParentFile().getPath())) {
            throw new RuntimeException("Invalid export path: " + file.getPath());
        }

        if(file.exists()) {
            if(!file.delete()) {
                throw new RuntimeException("Could not delete file: " + file.getAbsolutePath());
            }
        }

        if(!file.createNewFile()) {
            throw new RuntimeException("Unable to create file: " + file.getAbsolutePath());
        }

        final YamlHelper yamlHelper = new YamlHelper(file);
        yamlHelper.set("openapi", "3.0.4");
        yamlHelper.set("info.title", "Exported with THB Swagger Export Tool");
        yamlHelper.set("info.description", "Project directory: " + springBootExtractor.getPath());
        yamlHelper.set("info.version", "0.1.9");

        springBootExtractor.getSpringAllPaths().forEach(springPath -> {
            final String yamlPath = "paths." + springPath.path() + "." + springPath.method().toLowerCase();

            yamlHelper.set(yamlPath + ".summary", "");
            yamlHelper.set(yamlPath + ".description", "");

            if(springPath.produces() != null) {
                for(Expression expression : springPath.produces()) {
                    final String outputFormat = expression.asStringLiteralExpr().getValue();

                    // Description & maybe summary
                    x: for(AnnotationExpr annotationExpr : springPath.methodDeclaration().getAnnotations()) {
                        if(annotationExpr.getNameAsString().equalsIgnoreCase(CommonAnnotationNameRegistry.API_OPERATION.toString()) && springPath.javaFileContainer().hasImport(AnnotationRegistry.SWAGGER_API_OPERATION.toString())) {
                            for(MemberValuePair memberValuePair : annotationExpr.asNormalAnnotationExpr().getPairs()) {
                                if(memberValuePair.getNameAsString().equals(AnnotationValueRegistry.API_OPERATION_VALUE.toString())) {
                                    yamlHelper.set(yamlPath + ".responses." + springPath.responseStatus() + ".description", memberValuePair.getValue().asStringLiteralExpr().getValue());
                                    break x;
                                }
                            }
                        }
                    }

                    // Response
                    SwaggerTypeGenerator.processReturnType(springPath.methodDeclaration(), yamlHelper, springPath.responseStatus(), yamlPath, outputFormat, springBootExtractor, springPath.javaFileContainer());
                }
            }

            // RequestBody
            final MethodDeclaration methodDeclaration = springPath.methodDeclaration();
            for(Parameter parameter : methodDeclaration.getParameters()) {
                boolean isRequestBody = false;
                for(AnnotationExpr annotationExpr : parameter.getAnnotations()) {
                    if(annotationExpr instanceof MarkerAnnotationExpr markerAnnotationExpr) {
                        if (markerAnnotationExpr.getNameAsString().equals(CommonAnnotationNameRegistry.REQUEST_BODY.toString())) {
                            isRequestBody = true;
                        }
                    }
                }

                if(!isRequestBody) {
                    continue;
                }

                final Type type = parameter.getType();
                final Map<String, Object> typeMap = SwaggerTypeGenerator.getSwaggerSchemaForReturnType(type, yamlHelper, springBootExtractor, springPath.javaFileContainer());

                if(springPath.consumes() != null) {
                    for(Expression expression : springPath.consumes()) {
                        final String outputFormat = expression.asStringLiteralExpr().getValue();
                        yamlHelper.set(yamlPath + ".requestBody.content." + outputFormat + ".schema", typeMap);
                    }
                }
            }
        });

        yamlHelper.save();
    }
}
