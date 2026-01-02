package thb.mdsd.spring;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.*;
import lombok.Getter;
import lombok.NonNull;
import thb.mdsd.spring.data.AnnotationRegistry;
import thb.mdsd.spring.data.AnnotationValueRegistry;
import thb.mdsd.spring.data.CommonAnnotationNameRegistry;
import thb.mdsd.spring.data.HttpStatus;
import thb.mdsd.spring.extractor.container.JavaClassAnnotationContainer;
import thb.mdsd.spring.extractor.JavaFile;
import thb.mdsd.spring.extractor.container.JavaMethodAnnotationContainer;
import thb.mdsd.spring.extractor.container.SpringPathContainer;
import thb.mdsd.util.PathUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SpringBootExtractor {

    @Getter
    private final String path;
    private List<JavaFile> containerList;

    public SpringBootExtractor(@NonNull String path) {
        if(!PathUtils.isPath(path)) {
            throw new RuntimeException(path + " is not a valid path.");
        }

        this.path = path;
        this.containerList = null;
    }

    /**
     * Start the extraction process.
     */
    public void startExtracting() {
        final List<JavaFile> containerList = new LinkedList<>();

        PathUtils.extractRecursively(this.path).forEach(file -> {
            try(final FileInputStream fileInputStream = new FileInputStream(file)) {
                final byte[] data = fileInputStream.readAllBytes();
                final JavaFile container = new JavaFile(file, new String(data, StandardCharsets.UTF_8));
                containerList.addLast(container);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        this.containerList = containerList;
    }

    /**
     * Little helper method that checks if this instance has already extracted all data from target directory.
     */
    private void checkExtracted() {
        if(this.containerList == null) {
            throw new RuntimeException("Extraction results not found. Please call #startExtracting() before using this method.");
        }
    }

    /**
     * Check if this instance has extracted all data.
     * @return True if the instance has extracted all data, otherwise false.
     */
    public boolean hasExtracted() {
        return this.containerList != null;
    }

    /**
     * Find a specific subset of annotations in the given instance context.
     * @param commonNames Required list of common annotation name to find.
     * @param importRegistry Optional list of import registry names. "null" disables this check.
     * @throws RuntimeException If #startExtraction was not called before.
     * @return A list of {@link JavaFile} references that match all conditions
     */
    public List<JavaFile> findSpecificAnnotation(@NonNull List<CommonAnnotationNameRegistry> commonNames, List<AnnotationRegistry> importRegistry) {
        checkExtracted();

        final List<JavaFile> entityContainerList = new LinkedList<>();
        this.containerList.forEach(javaFileContainer -> {
            final List<JavaClassAnnotationContainer> classAnnotations = javaFileContainer.findClassAnnotations();
            for(JavaClassAnnotationContainer classAnnotation : classAnnotations) {
                for(AnnotationExpr annotationExpr : classAnnotation.annotations()) {
                    if(importRegistry != null) {
                        final Optional<ImportDeclaration> optionalImportDeclaration = javaFileContainer.findAnnotationPackage(annotationExpr);
                        if (optionalImportDeclaration.isEmpty()) {
                            continue;
                        }

                        final ImportDeclaration importDeclaration = optionalImportDeclaration.get();
                        if(commonNames.stream().noneMatch(commonAnnotationNameRegistry -> annotationExpr.getNameAsString().equals(commonAnnotationNameRegistry.toString()))) {
                            continue;
                        }

                        if(importRegistry.stream().anyMatch(annotationRegistry -> importDeclaration.getNameAsString().equals(annotationRegistry.toString()))) {
                            entityContainerList.addLast(javaFileContainer);
                        }
                    } else {
                        if(commonNames.stream().anyMatch(commonAnnotationNameRegistry -> annotationExpr.getNameAsString().equals(commonAnnotationNameRegistry.toString()))) {
                            entityContainerList.addLast(javaFileContainer);
                        }
                    }
                }
            }
        });

        return entityContainerList;
    }

    /**
     * Find all SpringBoot @Entity classes in the given project.
     * @param importCheck Should the algorithm check if the import statement is given?
     * @return A list of {@link JavaFile} references
     */
    public List<JavaFile> findAllSpringEntities(boolean importCheck) {
        return this.findSpecificAnnotation(List.of(CommonAnnotationNameRegistry.ENTITY), importCheck ? List.of(AnnotationRegistry.JAKARTA_PERSISTENCE_ENTITY, AnnotationRegistry.JAKARTA_PERSISTENCE_ANY) : null);
    }

    /**
     * Find all SpringBoot @RestController or @Controller classes in the given project.
     * @param importCheck Should the algorithm check if the import statement is given?
     * @return A list of {@link JavaFile} references
     */
    public List<JavaFile> findAllSpringControllers(boolean importCheck) {
        return this.findSpecificAnnotation(List.of(CommonAnnotationNameRegistry.CONTROLLER, CommonAnnotationNameRegistry.REST_CONTROLLER), importCheck ? List.of(AnnotationRegistry.SPRING_WEB_ANY, AnnotationRegistry.SPRING_WEB_REST_CONTROLLER, AnnotationRegistry.SPRING_WEB_CONTROLLER) : null);
    }

    /**
     * Find a specific Class declaration by its name and package declaration.
     * @param className Class name
     * @param packageName Package name
     * @return {@link JavaFile} if found, otherwise null.
     */
    public JavaFile findClass(@NonNull String className, @NonNull String packageName) {
        for(JavaFile container : this.containerList) {
            final String localPackageName = container.getPackage();
            if(!packageName.equals(localPackageName)) continue;

            final List<ClassOrInterfaceDeclaration> classOrInterfaceDeclarations = container.getClassOrInterfaceDeclarations();
            for(ClassOrInterfaceDeclaration declaration : classOrInterfaceDeclarations) {
                if(declaration.getNameAsString().equals(className)) {
                    return container;
                }
            }
        }

        return null;
    }

    public boolean isSwaggerEnabled() {
        return !findSpecificAnnotation(List.of(CommonAnnotationNameRegistry.ENABLE_SWAGGER_2), List.of(AnnotationRegistry.SWAGGER_ENABLE)).isEmpty();
    }

    private String getSpringHeaderRequestPath(@NonNull JavaFile javaFileContainer) {
        final List<JavaClassAnnotationContainer> classAnnotations = javaFileContainer.findClassAnnotations();

        for(JavaClassAnnotationContainer classAnnotationContainer : classAnnotations) {
            String path = null;
            boolean hasRestController = false;

            for(AnnotationExpr annotationExpr : classAnnotationContainer.annotations()) {
                if(annotationExpr.getNameAsString().equals(CommonAnnotationNameRegistry.REST_CONTROLLER.toString())) {
                    hasRestController = true;
                }

                if(annotationExpr.getNameAsString().equals(CommonAnnotationNameRegistry.REQUEST_MAPPING.toString())) {
                    final List<MemberValuePair> pairs = annotationExpr.asNormalAnnotationExpr().getPairs();
                    for(final MemberValuePair pair : pairs) {
                        final String key = pair.getNameAsString();
                        final Expression expression = pair.getValue();

                        if (key.equals(AnnotationValueRegistry.REQUEST_MAPPING_PATH.toString())) {
                            if (expression instanceof StringLiteralExpr stringLiteralExpr) {
                                path = stringLiteralExpr.getValue();
                            } else {
                                System.err.println("Can not extract value-path from RequestMapping: " + javaFileContainer.getPackage());
                            }
                        }
                    }
                }
            }

            if(hasRestController) {
                return path == null ? "" : path;
            }
        }

        return "";
    }

    public List<SpringPathContainer> getSpringAllPaths() {
        final List<JavaFile> containerList = findSpecificAnnotation(List.of(CommonAnnotationNameRegistry.REQUEST_MAPPING, CommonAnnotationNameRegistry.RESPONSE_STATUS), null);
        final List<SpringPathContainer> output = new LinkedList<>();

        for(JavaFile javaFileContainer : containerList) {
            final String headerPath = getSpringHeaderRequestPath(javaFileContainer);
            final List<JavaMethodAnnotationContainer> classAnnotations = javaFileContainer.findMethodAnnotations();
            for(JavaMethodAnnotationContainer methodAnnotationContainer : classAnnotations) {
                String value = null, method = null;
                String responseStatus = null;
                NodeList<Expression> consumes = null, produces = null;

                for(AnnotationExpr annotationExpr : methodAnnotationContainer.annotations()) {
                    if(annotationExpr.getNameAsString().equals(CommonAnnotationNameRegistry.REQUEST_MAPPING.toString())) {
                        final List<MemberValuePair> pairs = annotationExpr.asNormalAnnotationExpr().getPairs();
                        for(final MemberValuePair pair : pairs) {
                            final String key = pair.getNameAsString();
                            final Expression expression = pair.getValue();

                            if(key.equals(AnnotationValueRegistry.REQUEST_MAPPING_PATH.toString())) {
                                if (expression instanceof StringLiteralExpr stringLiteralExpr) {
                                    final String literal = stringLiteralExpr.getValue();
                                    value = headerPath + (literal.startsWith("/") ? literal : "/" + literal);
                                } else {
                                    throw new RuntimeException("Can not extract value-path from RequestMapping: " + javaFileContainer.getPackage());
                                }
                            } else if(key.equals(AnnotationValueRegistry.REQUEST_MAPPING_METHOD.toString())) {
                                if (expression instanceof FieldAccessExpr fieldAccessExpr) {
                                    method = fieldAccessExpr.getNameAsString();
                                } else {
                                    throw new RuntimeException("Can not extract method from RequestMapping: " + javaFileContainer.getPackage());
                                }
                            } else if(key.equals(AnnotationValueRegistry.REQUEST_MAPPING_CONSUMES.toString())) {
                                if (expression instanceof ArrayInitializerExpr arrayInitializerExpr) {
                                    consumes = arrayInitializerExpr.getValues();
                                } else {
                                    throw new RuntimeException("Can not extract consumes method from RequestMapping: " + javaFileContainer.getPackage());
                                }
                            } else if(key.equals(AnnotationValueRegistry.REQUEST_MAPPING_PRODUCES.toString())) {
                                if (expression instanceof ArrayInitializerExpr arrayInitializerExpr) {
                                    produces = arrayInitializerExpr.getValues();
                                } else {
                                    throw new RuntimeException("Can not extract produces method from RequestMapping: " + javaFileContainer.getPackage());
                                }
                            }
                        }
                    } else if(annotationExpr.getNameAsString().equals(CommonAnnotationNameRegistry.RESPONSE_STATUS.toString())) {
                        responseStatus = annotationExpr.asSingleMemberAnnotationExpr().getMemberValue().toString();
                    }
                }

                if(value == null) {
                    value = "";
                }

                if(method == null) {
                    throw new RuntimeException("Invalid SpringBoot project. Found method with @RequestMapping without method.");
                }

                int status;
                if(responseStatus == null) {
                    status = 200;
                } else {
                    try {
                        status = HttpStatus.valueOf(responseStatus).getValue();
                    } catch (IllegalArgumentException exception) {
                        status = 200;
                    }
                }

                output.addLast(new SpringPathContainer(javaFileContainer, methodAnnotationContainer.declaration(), value, method, status, consumes, produces));
            }
        }

        return output;
    }

    /**
     * Free memory but keep instance
     */
    public void dispose() {
        this.containerList = null;
    }
}
