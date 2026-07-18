package yee.pltision.soa.processor;

import com.palantir.javapoet.*;
import yee.pltision.soa.annotation.Field;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Locale.ENGLISH;

@SupportedAnnotationTypes("yee.pltision.soa.annotation.SoA")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class SoAProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element elem : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (elem.getKind() == ElementKind.RECORD) {
                    generateStoreForRecord((TypeElement) elem);
                } else if (elem.getKind() == ElementKind.CLASS) {
                    // TODO: support class
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@SoA can only be used on Record or Class", elem);
                }
            }
        }
        return true;
    }

    private boolean generateStoreForRecord(TypeElement recordElem) {
        String packageName = processingEnv.getElementUtils().getPackageOf(recordElem).getQualifiedName().toString();
        String simpleName = recordElem.getSimpleName().toString();
        String storeName = simpleName + "Store";

        List<? extends RecordComponentElement> components = recordElem.getRecordComponents();
        if (components.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "No record components found for @SoA", recordElem);
            return false;
        }

        Optional<List<GroupInfo>> optional = getGroups(
                getFieldFromRecord(components),
                simpleName.toLowerCase(Locale.ROOT)
        );
        if (!optional.isPresent()) {
            return false;
        }

        List<GroupInfo> groups = optional.get();
        int fieldCount = components.size();
        ClassName recordClass = ClassName.get(recordElem);

        return generateStore(groups, fieldCount, recordClass, packageName, simpleName, storeName);
    }

    private boolean generateStore(List<GroupInfo> groups,
                                  int fieldCount,
                                  ClassName recordClass,
                                  String packageName,
                                  String simpleName,
                                  String storeName) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(ClassName.get(packageName, storeName))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // 1. size field
        classBuilder.addField(FieldSpec.builder(int.class, "size")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build());

        // 2. 收集所有字段并按 constructIndex 排序
        List<FieldInfo> allFields = groups.stream()
                .flatMap(g -> g.fields().stream())
                .sorted(Comparator.comparingInt(FieldInfo::constructIndex))
                .collect(Collectors.toList());

        // 存储每个组的数组字段名，用于后续引用
        Map<String, FieldSpec> groupArrayFields = new LinkedHashMap<>();

        // 3. 为每个组生成静态 size 和实例数组字段
        for (GroupInfo group : groups) {
            int groupSize = group.fields().size();
            TypeName elementType = TypeName.get(group.type());
            ArrayTypeName arrayType = ArrayTypeName.of(elementType);
            String arrayFieldName = group.name() + "Array";

            // groupSize 静态常量
            classBuilder.addField(FieldSpec.builder(int.class, group.name() + "Size")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$L", groupSize)
                    .build());

            // 数组字段
            FieldSpec arrayField = FieldSpec.builder(arrayType, arrayFieldName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .build();
            classBuilder.addField(arrayField);
            groupArrayFields.put(group.name(), arrayField);
        }

        // 4. 为每个字段生成 OFFSET 和 SIZE 常量，以及 getter / setter
        // 同时收集所有字段的 getter 方法名，供 get() 使用
        List<MethodSpec> fieldGetters = new ArrayList<>();
        Map<FieldInfo, MethodSpec> fieldSetterMap = new HashMap<>();

        for (GroupInfo group : groups) {
            String groupName = group.name();
            FieldSpec arrayField = groupArrayFields.get(groupName);
            int offset = 0;
            for (FieldInfo field : group.fields()) {
                String fieldName = field.name();
                TypeName fieldType = TypeName.get(field.type());
                String cap = capitalize(fieldName);

                // OFFSET
                classBuilder.addField(FieldSpec.builder(int.class, fieldName.toUpperCase() + "_OFFSET")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", offset)
                        .build());

                // SIZE (固定为1)
                classBuilder.addField(FieldSpec.builder(int.class, fieldName.toUpperCase() + "_SIZE")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", 1)
                        .build());

                // getter
                MethodSpec getter = MethodSpec.methodBuilder("get" + cap)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(fieldType)
                        .addParameter(int.class, "index")
                        .addStatement("return $N[index * $N + $N]",
                                arrayField, fieldName.toUpperCase() + "_OFFSET", fieldName.toUpperCase() + "_OFFSET")
                        .build();
                classBuilder.addMethod(getter);
                fieldGetters.add(getter);

                // setter (单个字段)
                MethodSpec setter = MethodSpec.methodBuilder("set" + cap)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(int.class, "index")
                        .addParameter(fieldType, fieldName)
                        .addStatement("$N[index * $N + $N] = $N",
                                arrayField, fieldName.toUpperCase() + "_OFFSET", fieldName.toUpperCase() + "_OFFSET", fieldName)
                        .build();
                classBuilder.addMethod(setter);
                fieldSetterMap.put(field, setter);

                offset++;
            }

            // setGroup 方法
            MethodSpec.Builder groupSetter = MethodSpec.methodBuilder("set" + capitalize(groupName))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, "index");
            for (FieldInfo field : group.fields()) {
                groupSetter.addParameter(TypeName.get(field.type()), field.name());
            }
            CodeBlock.Builder groupBody = CodeBlock.builder();
            for (FieldInfo field : group.fields()) {
                groupBody.addStatement("$N(index, $N)", fieldSetterMap.get(field), field.name());
            }
            groupSetter.addCode(groupBody.build());
            classBuilder.addMethod(groupSetter.build());
        }

        // 5. 构造函数
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "size")
                .addStatement("this.size = size");
        for (GroupInfo group : groups) {
            FieldSpec arrayField = groupArrayFields.get(group.name());
            int groupSize = group.fields().size();
            constructor.addStatement("this.$N = new $T[size * $L]",
                    arrayField, TypeName.get(group.type()), groupSize);
        }
        classBuilder.addMethod(constructor.build());

        // 6. get(int) 方法：构造 Record 对象
        MethodSpec.Builder getElement = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "index")
                .returns(recordClass);
        StringBuilder getBody = new StringBuilder("return new $T(");
        List<Object> getArgs = new ArrayList<>();
        getArgs.add(recordClass);
        for (int i = 0; i < allFields.size(); i++) {
            if (i > 0) getBody.append(", ");
            FieldInfo field = allFields.get(i);
            // 调用对应的 getter
            getBody.append("$N(index)");
            getArgs.add("get" + capitalize(field.name()));
        }
        getBody.append(");");
        getElement.addCode(getBody.toString(), getArgs.toArray());
        classBuilder.addMethod(getElement.build());

        // 7. set(int, Record) 方法
        MethodSpec.Builder setElement = MethodSpec.methodBuilder("set")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "index")
                .addParameter(recordClass, simpleName.toLowerCase(Locale.ROOT));
        for (FieldInfo field : allFields) {
            String paramName = simpleName.toLowerCase(Locale.ROOT);
            setElement.addStatement("$N(index, $N.$N())",
                    fieldSetterMap.get(field), paramName, field.name());
        }
        classBuilder.addMethod(setElement.build());

        // 8. 写入文件
        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .build();
        try {
            javaFile.writeTo(processingEnv.getFiler());
            return false;
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate store: " + e.getMessage());
            return true;
        }
    }

    // ------------------- 辅助方法 -------------------

    private Optional<List<GroupInfo>> getGroups(List<FieldInfo> fieldInfos, String defaultGroup) {
        Map<String, GroupInfo> groupMap = new LinkedHashMap<>();
        Set<String> multipleTypeGroups = new HashSet<>();

        for (FieldInfo field : fieldInfos) {
            String groupName = field.group().isEmpty() ? defaultGroup : field.group();
            GroupInfo group = groupMap.computeIfAbsent(groupName,
                    g -> new GroupInfo(g, field.type(), new ArrayList<>()));
            if (!group.type().equals(field.type())) {
                multipleTypeGroups.add(groupName);
            }
            group.fields().add(field);
        }

        for (String groupName : multipleTypeGroups) {
            StringBuilder error = new StringBuilder("Group " + groupName + " has multiple types: {\n");
            for (FieldInfo field : fieldInfos) {
                if (field.group().equals(groupName)) {
                    error.append("\t").append(field.type().toString())
                            .append(" ").append(field.name()).append(",\n");
                }
            }
            error.append("}");
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error.toString());
        }

        if (!multipleTypeGroups.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ArrayList<>(groupMap.values()));
    }

    private List<FieldInfo> getFieldFromRecord(List<? extends RecordComponentElement> components) {
        List<FieldInfo> fields = new ArrayList<>();
        int i = 0;
        for (RecordComponentElement comp : components) {
            String name = comp.getSimpleName().toString();
            TypeMirror type = comp.asType();
            String group = getGroupFromComponent(comp);
            fields.add(new FieldInfo(name, type, group, i));
            i++;
        }
        return fields;
    }

    private String getGroupFromComponent(RecordComponentElement comp) {
        Field fieldAnno = comp.getAnnotation(Field.class);
        return fieldAnno != null ? fieldAnno.group() : "";
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }

    // ------------------- 内部记录类 -------------------
    private record FieldInfo(String name, TypeMirror type, String group, int constructIndex) {
    }

    private record GroupInfo(String name, TypeMirror type, List<FieldInfo> fields) {
    }
}