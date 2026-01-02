package thb.mdsd.swagger;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import lombok.NonNull;
import thb.mdsd.spring.SpringBootExtractor;
import thb.mdsd.spring.extractor.JavaFile;
import thb.mdsd.util.YamlHelper;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwaggerTypeGenerator {

    public static void processReturnType(@NonNull MethodDeclaration method, @NonNull YamlHelper yamlHelper, int response, @NonNull String path, @NonNull String outputFormat, @NonNull SpringBootExtractor springBootExtractor, @NonNull JavaFile currentContainer) {
        final Type returnType = method.getType();
        final Map<String, Object> schema = getSwaggerSchemaForReturnType(returnType, yamlHelper, springBootExtractor, currentContainer);
        final String schemaName = extractGeneric(returnType.asString());

        if (!isSimpleType(returnType) && !isVoid(returnType)) {
            final Object current = yamlHelper.get("components.schemas." + schemaName);
            if(current == null || current.toString().equals("{type=object}")) {
                yamlHelper.set("components.schemas." + schemaName, schema);
            }
        }

        final String responsePath = path + ".responses." + response + ".content." + outputFormat + ".schema";
        if (isCollection(returnType) || isArray(returnType) || isGenericArrayType(returnType)) {
            yamlHelper.set(responsePath + ".type", "array");
            yamlHelper.set(responsePath + ".items.$ref", "#/components/schemas/" + schemaName);
        } else {
            yamlHelper.set(responsePath + ".$ref", "#/components/schemas/" + schemaName);
        }
    }

    public static Map<String, Object> getSwaggerSchemaForReturnType(@NonNull Type returnType, @NonNull YamlHelper yamlHelper, @NonNull SpringBootExtractor springBootExtractor, @NonNull JavaFile currentContainer) {
        final Map<String, Object> schema = new HashMap<>();

        if (returnType instanceof ParameterizedType parameterizedType) {
            final String baseType = parameterizedType.getTypeName();

            if (baseType.equals("List") || baseType.equals("Set") || baseType.equals("Page")) {
                final Type itemType = (Type) parameterizedType.getActualTypeArguments()[0];
                parseItemType(yamlHelper, schema, itemType);
                schema.put("type", "array");
                final Map<String, Object> items = new HashMap<>();
                items.put("$ref", "#/components/schemas/" + itemType.asString());
                schema.put("items", items);
            }
        }

        if (returnType instanceof ArrayType arrayType) {
            final Type elementType = arrayType.getComponentType();
            parseItemType(yamlHelper, schema, elementType);
        }

        if (returnType instanceof ClassOrInterfaceType classOrInterfaceType) {
            final String typeName = classOrInterfaceType.getNameAsString();

            switch (typeName) {
                case "String" -> schema.put("type", "string");
                case "long", "Long" -> {
                    schema.put("type", "long");
                    schema.put("format", "int64");
                }
                case "int", "Integer" -> {
                    schema.put("type", "integer");
                    schema.put("format", "int32");
                }
                case "short", "Short" -> {
                    schema.put("type", "float");
                    schema.put("format", "int16");
                }
                case "byte", "Byte" -> {
                    schema.put("type", "byte");
                    schema.put("format", "int8");
                }
                case "float", "Float" -> {
                    schema.put("type", "float");
                    schema.put("format", "float32");
                }
                case "double", "Double" -> {
                    schema.put("type", "double");
                    schema.put("format", "float64");
                }
                case "boolean", "Boolean" -> schema.put("type", "boolean");
                case "void" -> {}
                default -> {
                    addSchemaForCustomType(returnType, yamlHelper);
                    schema.put("type", "object");
                    schema.put("properties", extractClassObject(returnType, springBootExtractor, currentContainer, new ArrayList<>()));
                }
            }
        }

        if (schema.isEmpty()) {
            schema.put("type", "object");
        }

        return schema;
    }

    private static HashMap<String, Object> extractClassObject(@NonNull Type type, @NonNull SpringBootExtractor springBootExtractor, @NonNull JavaFile currentContainer, @NonNull List<String> visited) {
        if(type instanceof ClassOrInterfaceType classOrInterfaceType) {
            visited.add(classOrInterfaceType.getNameAsString());
            final String name = classOrInterfaceType.getNameAsString();

            ImportDeclaration importDeclaration = null;
            for(ImportDeclaration declaration : currentContainer.getUnit().getImports()) {
                if(declaration.getNameAsString().endsWith(name)) {
                    importDeclaration = declaration;
                    break;
                }
            }

            ClassOrInterfaceDeclaration classOrInterfaceDeclaration = null;
            if(importDeclaration == null) { // Multiple classes defined?
                classOrInterfaceDeclaration = currentContainer.getClassOrInterfaceDeclaration(name);
            } else {
                String formattedPackage = importDeclaration.getNameAsString().substring(0, importDeclaration.getNameAsString().length() - name.length());
                if(formattedPackage.endsWith(".")) {
                    formattedPackage = formattedPackage.substring(0, formattedPackage.length() - 1);
                }

                final JavaFile javaFileContainer = springBootExtractor.findClass(name, formattedPackage);
                if(javaFileContainer != null) {
                    classOrInterfaceDeclaration = javaFileContainer.getClassOrInterfaceDeclaration(name);
                }
            }

            if(classOrInterfaceDeclaration == null) {
                return new HashMap<>();
            }

            final HashMap<String, Object> output = new HashMap<>();
            for (FieldDeclaration fieldDeclaration : classOrInterfaceDeclaration.getFields()) {
                for (VariableDeclarator variableDeclarator : fieldDeclaration.getVariables()) {
                    final String fieldName = variableDeclarator.getNameAsString();
                    final Type fieldType = variableDeclarator.getType();

                    final HashMap<String, Object> fieldMap = new HashMap<>();
                    if(!isSimpleType(fieldType) && fieldType instanceof ClassOrInterfaceType reference) {
                        if(visited.contains(reference.getNameAsString())) {
                            continue;
                        }

                        fieldMap.put("type", "object");
                        fieldMap.putAll(extractClassObject(fieldType, springBootExtractor, currentContainer, visited));
                    } else {
                        if(fieldType.isPrimitiveType()) {
                            final String fieldTypeName = fieldType.asString();
                            if(switch (fieldTypeName) {
                                case "long", "Long" -> {
                                    fieldMap.put("format", "int64");
                                    yield true;
                                }
                                case "int", "Integer" -> {
                                    fieldMap.put("format", "int32");
                                    yield true;
                                }
                                case "short", "Short", "char", "Char" -> {
                                    fieldMap.put("format", "int16");
                                    yield true;
                                }
                                case "byte", "Byte" -> {
                                    fieldMap.put("format", "int8");
                                    yield true;
                                }
                                case "float", "Float" -> {
                                    fieldMap.put("format", "float32");
                                    yield true;
                                }
                                case "double", "Double" -> {
                                    fieldMap.put("format", "float64");
                                    yield true;
                                }
                                default -> false;
                            }) {
                                fieldMap.put("type", "integer");
                            } else if(fieldTypeName.equalsIgnoreCase("boolean")) {
                                fieldMap.put("type", "boolean");
                            } else {
                                throw new RuntimeException("Invalid primitive datatype found: " + fieldTypeName);
                            }
                        } else {
                            if(fieldType.asString().equals("String")) {
                                fieldMap.put("type", "string");
                            } else {
                                fieldMap.put("type", fieldType.asString());
                            }
                        }
                    }

                    output.put(fieldName, fieldMap);
                }
            }

            return output;
        } else {
            return new HashMap<>();
        }
    }

    private static String extractGeneric(@NonNull String input) {
        final String regex = "<(.*)>";

        final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(input);

        if(matcher.find()) {
            String match = matcher.group(1);
            if(match.contains("<") && match.contains(">")) {
                return extractGeneric(match);
            } else {
                return match;
            }
        } else {
            return input;
        }
    }

    private static void parseItemType(@NonNull YamlHelper yamlHelper, @NonNull Map<String, Object> schema, @NonNull Type itemType) {
        final String itemTypeName = extractGeneric(itemType.asString());

        if (!isSimpleType(itemType)) {
            addSchemaForCustomType(itemType, yamlHelper);
        }

        schema.put("type", "array");
        final Map<String, Object> items = new HashMap<>();
        items.put("$ref", "#/components/schemas/" + itemTypeName);
        schema.put("items", items);
    }

    private static void addSchemaForCustomType(@NonNull Type type, @NonNull YamlHelper yamlHelper) {
        final String typeName = extractGeneric(type.asString());
        if (!isSimpleType(type)) {
            final String name = extractGeneric(typeName);
            yamlHelper.set("components.schemas." + name + ".type", "object");

            boolean apply = true;
            try {
                final ResolvedType resolvedType = type.resolve();
                if (resolvedType.isReferenceType()) {
                    final ResolvedReferenceTypeDeclaration decl = resolvedType.asReferenceType().getTypeDeclaration().get();
                    final List<ResolvedFieldDeclaration> fields = decl.getAllFields();
                    fields.forEach(field -> yamlHelper.set("components.schemas." + name + ".properties." + field.getName() + ".type", javaFieldTypeToSwagger(field.getType().describe())));
                }
            } catch (Exception _) {
                apply = false;
            }

            if(!apply) {
                yamlHelper.set("components.schemas." + name, null);
            }
        }
    }

    private static String javaFieldTypeToSwagger(@NonNull String name) {
        name = name.replace("java.lang.", "");
        return switch (name) {
            case "String" -> "string";
            case "long", "Long" -> "int64";
            case "int", "Integer" -> "int32";
            case "short", "Short" -> "int16";
            case "byte", "Byte" -> "int8";
            case "float", "Float" -> "float32";
            case "double", "Double" -> "float64";
            case "boolean", "Boolean" -> "boolean";
            case "void" -> "void";
            default -> "object";
        };
    }

    private static boolean isSimpleType(@NonNull Type type) {
        if (type instanceof ClassOrInterfaceType classOrInterfaceType) {
            final String name = classOrInterfaceType.getNameAsString();
            return name.equals("String")
                    || name.equals("short") || name.equals("int") || name.equals("float") || name.equals("long") || name.equals("double") || name.equals("boolean")
                    || name.equals("Short") || name.equals("Integer") || name.equals("Float") || name.equals("Long") || name.equals("Double") || name.equals("Boolean");
        }
        return false;
    }

    private static boolean isCollection(@NonNull Type type) {
        return type instanceof ParameterizedType || type instanceof ClassOrInterfaceType && (type.toString().contains("List") || type.toString().contains("Set") || type.toString().startsWith("Page"));
    }

    private static boolean isVoid(@NonNull Type type) {
        return type instanceof ClassOrInterfaceType classOrInterfaceType && classOrInterfaceType.getNameAsString().equals("void");
    }

    private static boolean isArray(@NonNull Type type) {
        return type instanceof ArrayType;
    }

    private static boolean isGenericArrayType(@NonNull Type type) {
        return type instanceof GenericArrayType;
    }
}
