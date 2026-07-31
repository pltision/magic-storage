package yee.pltision.soa.processor;

import cn.hutool.core.text.NamingCase;
import com.palantir.javapoet.*;
import org.jetbrains.annotations.Nullable;
import yee.pltision.soa.annotation.Field;
import yee.pltision.soa.annotation.Glue;
import yee.pltision.soa.annotation.SoA;
import yee.pltision.soa.processor.spi.ElementGlueProvider;

import javax.annotation.processing.*;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("yee.pltision.soa.annotation.SoA")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class SoAProcessor extends AbstractProcessor {

    private final ClassName annotationClassName = ClassName.get(SoA.class);

    // 缓存所有 SPI 提供者，键为 glue 类的 TypeMirror
    private Map<TypeMirror, ElementGlueProvider> providerMap;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        providerMap = new HashMap<>();
        try {
            ServiceLoader<ElementGlueProvider> loader =
                    ServiceLoader.load(ElementGlueProvider.class, getClass().getClassLoader());
            for (ElementGlueProvider provider : loader) {
                // 获取 provider 实现类的 TypeMirror
                TypeElement typeElem = processingEnv.getElementUtils()
                        .getTypeElement(provider.getClass().getCanonicalName());
                if (typeElem != null) {
                    providerMap.put(typeElem.asType(), provider);
//                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
//                            "Loaded glue provider: " + provider.getClass().getName());
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                            "Cannot resolve TypeElement for provider: " + provider.getClass().getName());
                }
            }
        } catch (Throwable t) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "Failed to load element glue providers: " + t);
            providerMap = Collections.emptyMap(); // 空而不是 null，便于后续检查
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean anySuccess=false;
        for (TypeElement annotation : annotations) {
            for (Element elem : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (elem.getKind() == ElementKind.RECORD) {
                    try {
                        anySuccess |= generateStoreForRecord((TypeElement) elem);
                    }
                    catch (Throwable t){
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, t.toString());
                        t.printStackTrace();
                    }
                } else if (elem.getKind() == ElementKind.CLASS) {
                    // TODO: support class
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@" + annotationClassName.simpleName() + " can only be used on Record or Class", elem);
                }
            }
        }
        return anySuccess;
    }

    private boolean generateStoreForRecord(TypeElement recordElem) {
        String packageName = processingEnv.getElementUtils().getPackageOf(recordElem).getQualifiedName().toString();
        String simpleName = recordElem.getSimpleName().toString();
        String storeName = simpleName + "Store";

        List<? extends RecordComponentElement> components = recordElem.getRecordComponents();
        if (components.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "No record components found for @" + annotationClassName.simpleName(), recordElem);
            return false;
        }

        Optional<List<FieldInfo>> fieldInfosOpt = getFieldFromRecord(components);
        if (fieldInfosOpt.isEmpty()) {
            return false;
        }

        Optional<List<GroupInfo>> groupsOpt = getGroups(
                fieldInfosOpt.get(),
                NamingCase.toCamelCase(simpleName)
        );
        if (groupsOpt.isEmpty()) {
            return false;
        }

        List<GroupInfo> groups = groupsOpt.get();
        ClassName recordClass = ClassName.get(recordElem);

        return generateStore(groups, recordClass, packageName, simpleName, storeName);
    }

    // ------------------- 核心生成方法 -------------------

    private boolean generateStore(List<GroupInfo> groups,
                                  ClassName recordClass,
                                  String packageName,
                                  String simpleName,
                                  String storeName) {
        // 构建数组
        List<GroupSpec> groupSpecs = new ArrayList<>();
        for (GroupInfo group : groups) {
            String groupName = group.name();

            //直接数，field和group其实相互依赖，但group可以暂时全用标量
            int sizeCount = 0;
            for(FieldInfo field: group.fields){
                sizeCount+=field.dataLength;
            }
            TypeName elementType = group.dataType();
            ArrayTypeName arrayType = ArrayTypeName.of(elementType);

            String sizeConstName = NamingCase.toUnderlineCase(groupName).toUpperCase() + "_SIZE";
            FieldSpec sizeConst = FieldSpec.builder(int.class, sizeConstName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$L", sizeCount)
                    .build();

            String arrayFieldName = groupName + "Array";
            FieldSpec arrayField = FieldSpec.builder(arrayType, arrayFieldName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .build();

            groupSpecs.add(new GroupSpec(groupName, elementType, sizeCount, sizeConstName, arrayFieldName, sizeConst, arrayField));
        }

        String indexName = "elementIndex";

        // 构建字段 arrayGetter arraySetter 等需要被调用的函数
        List<FieldSpecs> fieldSpecsList = new ArrayList<>();

        for (GroupSpec gSpec : groupSpecs) {
            GroupInfo groupInfo = groups.stream()
                    .filter(g -> g.name().equals(gSpec.name))
                    .findFirst()
                    .orElseThrow();
            List<FieldInfo> fields = groupInfo.fields();

            int offset = 0;
            for (FieldInfo field : fields) {
                String fieldName = field.name();
                TypeName fieldType = field.filedType();
                String capFileName = NamingCase.toPascalCase(fieldName);

                String offsetConstName = NamingCase.toUnderlineCase(fieldName).toUpperCase() + "_OFFSET";
                FieldSpec offsetField = FieldSpec.builder(int.class, offsetConstName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", offset)
                        .build();


                String sizeConstName = NamingCase.toUnderlineCase(fieldName).toUpperCase() + "_SIZE";
                FieldSpec sizeConst = FieldSpec.builder(int.class, sizeConstName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", field.dataLength)
                        .build();

                // 分裂胶水和原初类型

                if(field.code ==null){
                    // float getField(int index)
                    MethodSpec getter = MethodSpec.methodBuilder("get" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .returns(fieldType)
                            .addStatement("return $N[$N * $N + $N]",
                                    gSpec.arrayField, indexName, gSpec.sizeConstName, offsetConstName)
                            .build();

                    // float setField(int index, float f)
                    MethodSpec setter = MethodSpec.methodBuilder("set" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .addParameter(fieldType, fieldName)
                            .addStatement("$N[$N * $N + $N] = $N",
                                    gSpec.arrayField, indexName, gSpec.sizeConstName, offsetConstName, fieldName)
                            .build();

                    FieldSpecs fSpec = new FieldSpecs(field, offsetField, sizeConst,
                            getter, null,
                            setter, null
                    );
                    fieldSpecsList.add(fSpec);
                }
                else {

                    // F getField(int index)
                    MethodSpec getter = MethodSpec.methodBuilder("get" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .returns(fieldType)
                            .addCode(field.code.getField(), field.filedType, gSpec.arrayField,
                                    CodeBlock.of("($N * $N + $N)", indexName, gSpec.sizeField, offsetConstName).toString())
                            .build();

                    String destName="dest";
                    // F getField(int index, F dest)
                    MethodSpec getWithDist = MethodSpec.methodBuilder("get" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .addParameter(fieldType, destName)
                            .returns(fieldType)
                            .addCode(field.code.getFieldToDest(), destName, gSpec.arrayField,
                                    CodeBlock.of("($N * $N + $N)", indexName, gSpec.sizeField, offsetConstName).toString())
                            .build();

                    // F setField(int index, F field)
                    MethodSpec setter = MethodSpec.methodBuilder("set" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .addParameter(fieldType, fieldName)
                            .addCode(field.code.setField(),
                                    fieldName,
                                    gSpec.arrayField,
                                    CodeBlock.of("($N * $N + $N)", indexName, gSpec.sizeField, offsetConstName).toString())
                            .build();

                    // F setField(int index, float... data)
                    MethodSpec.Builder setByPrimitive = MethodSpec.methodBuilder("set"+capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName);

                    String[] args = field.code.args();
                    for(int i=0;i<args.length;i++){
                        setByPrimitive.addParameter(field.dataType, args[i]);
                        setByPrimitive.addStatement("$N[$N * $N + $N + $L] = $N",
                                gSpec.arrayField,
                                gSpec.sizeField, indexName, offsetField, i,
                                args[i]
                        );
                    }

                    FieldSpecs fSpec = new FieldSpecs(field, offsetField, sizeConst,
                            getter,getWithDist,
                            setter, setByPrimitive.build()
                    );
                    fieldSpecsList.add(fSpec);
                }


                offset+=field.dataLength;
            }
        }

        // 构建类
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(ClassName.get(packageName, storeName))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Generate by $T with @$N\n",
                        ClassName.get(packageName,simpleName), annotationClassName.simpleName()
                )
                .addJavadoc("@see $N\n", annotationClassName.toString())    //显示全名
                .addJavadoc("@see $T\n", ClassName.get(packageName,simpleName))
                ;

        classBuilder.addField(FieldSpec.builder(int.class, "size")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build());

        for (GroupSpec gSpec : groupSpecs) {
            classBuilder.addField(gSpec.sizeField);
            classBuilder.addField(gSpec.arrayField);
        }

        for (FieldSpecs fSpec : fieldSpecsList) {
            classBuilder.addField(fSpec.offsetConst);
            classBuilder.addField(fSpec.sizeConst);
            classBuilder.addMethod(fSpec.arrayGetter);
            classBuilder.addMethod(fSpec.arraySetter);
            if(fSpec.arrayGetWithDist!=null) classBuilder.addMethod(fSpec.arrayGetWithDist);
            if(fSpec.arraySetPrimitive!=null) classBuilder.addMethod(fSpec.arraySetPrimitive);
        }

        // setGroup(int index, ... data)
        for (GroupSpec gSpec : groupSpecs) {
            GroupInfo groupInfo = groups.stream()
                    .filter(g -> g.name().equals(gSpec.name))
                    .findFirst()
                    .orElseThrow();
            List<FieldInfo> fields = groupInfo.fields();

            MethodSpec.Builder groupSetter = MethodSpec.methodBuilder("set" + NamingCase.toPascalCase(gSpec.name))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, indexName);
            for (FieldInfo field : fields) {
                groupSetter.addParameter(field.filedType(), field.name());
            }
            CodeBlock.Builder body = CodeBlock.builder();
            for (FieldInfo field : fields) {
                //通过constructIndex获取field，其实我觉得放group里面合适，但反正能用
                body.addStatement("$N($N, $N)",
                        fieldSpecsList.get(field.constructIndex).arraySetter,
                        indexName, field.name()
                );
            }
            groupSetter.addCode(body.build());
            classBuilder.addMethod(groupSetter.build());
        }

        // 构造函数
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "size")
                .addStatement("this.size = size");
        for (GroupSpec gSpec : groupSpecs) {
            constructor.addStatement("this.$N = new $T[size * $N]",
                    gSpec.arrayField, gSpec.elementType, gSpec.sizeConstName);
        }
        classBuilder.addMethod(constructor.build());

        // Element getElement(int index)
        List<FieldInfo> allFields = groups.stream()
                .flatMap(g -> g.fields().stream())
                .sorted(Comparator.comparingInt(FieldInfo::constructIndex))
                .collect(Collectors.toList());

        MethodSpec.Builder getElement = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, indexName)
                .returns(recordClass);
        StringBuilder getBody = new StringBuilder("return new $T(");
        List<Object> getArgs = new ArrayList<>();
        getArgs.add(recordClass);
        for (int i = 0; i < allFields.size(); i++) {
            if (i > 0) getBody.append(", ");
            FieldInfo field = allFields.get(i);
            getBody.append("$N($N)");
            getArgs.add(fieldSpecsList.get(field.constructIndex).arrayGetter);
            getArgs.add(indexName);
        }
        getBody.append(");");
        getElement.addCode(getBody.toString(), getArgs.toArray());
        classBuilder.addMethod(getElement.build());

        // void setElement(int index, E element)
        MethodSpec.Builder setElement = MethodSpec.methodBuilder("set")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, indexName)
                .addParameter(recordClass, NamingCase.toCamelCase(simpleName));
        String recordParam = NamingCase.toCamelCase(simpleName);
        for (FieldInfo field : allFields) {
            setElement.addStatement("$N($N, $N.$N())",
                    fieldSpecsList.get(field.constructIndex).arraySetter,
                    indexName,
                    recordParam, field.name()
            );
        }
        classBuilder.addMethod(setElement.build());

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .build();
        try {
            javaFile.writeTo(processingEnv.getFiler());
            return true;
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate store: " + e.getMessage());
            return false;
        }
    }

    // ------------------- 辅助方法 -------------------

    private Optional<List<GroupInfo>> getGroups(List<FieldInfo> fieldInfos, String defaultGroup) {
        Map<String, GroupInfo> groupMap = new LinkedHashMap<>();
        Set<GroupInfo> multipleTypeGroups = new HashSet<>();

        for (FieldInfo field : fieldInfos) {
            String groupName = field.group().isEmpty() ? defaultGroup : field.group();
            GroupInfo group = groupMap.computeIfAbsent(groupName,
                    g -> new GroupInfo(g, field.dataType, new ArrayList<>()));
            if (!group.dataType().equals(field.dataType)) {
                multipleTypeGroups.add(group);
            }
            group.fields().add(field);
        }

        if (multipleTypeGroups.isEmpty()) {
            return Optional.of(new ArrayList<>(groupMap.values()));
        }

        for (GroupInfo group : multipleTypeGroups) {
            StringBuilder error = new StringBuilder("Group " + group.name + " has multiple types: {\n");
            for (FieldInfo field : group.fields) {
                error.append("\t").append(field.dataType.toString())
                        .append(" ").append(field.name()).append(";\n");
            }
            error.append("}");
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error.toString());
        }
        return Optional.empty();
    }

    private Optional<List<FieldInfo>> getFieldFromRecord(List<? extends RecordComponentElement> components) {
        List<FieldInfo> fields = new ArrayList<>();
        int i = 0;
        boolean hasError = false;
        for (RecordComponentElement comp : components) {
            try {
                String name = comp.getSimpleName().toString();
                FieldCodeBlock fieldCodeBlock = getElementSpecsFromComponent(comp, comp.asType());
                TypeName dataType;
                int dataLength;
                TypeName fieldType;
                if (fieldCodeBlock == null) {
                    dataType = TypeName.get(comp.asType());
                    fieldType = dataType;
                    dataLength = 1;
                } else {
                    dataType = fieldCodeBlock.dataType();
                    fieldType = fieldCodeBlock.fliedType();
                    //args就是标量
                    dataLength = fieldCodeBlock.args().length;
                }
                String group = getGroupFromComponent(comp);
                fields.add(new FieldInfo(name, dataType, dataLength, fieldType, group, i, fieldCodeBlock));
                i++;
            } catch (RuntimeException t) {   // 捕获能得出信息的异常
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Error processing record component '" + comp.getSimpleName() + "': " + t.getMessage(),
                        comp
                );
                t.printStackTrace();
                hasError = true;
            }
        }
        if (hasError) {
            return Optional.empty();
        }
        return Optional.of(fields);
    }

    private String getGroupFromComponent(Element comp) {
        Field fieldAnno = comp.getAnnotation(Field.class);
        return fieldAnno != null ? fieldAnno.group() : "";
    }

    private FieldCodeBlock getElementSpecsFromComponent(AnnotatedConstruct comp, TypeMirror compClass) throws RuntimeException {
        List<AnnotationMirror> glueMirrors = new ArrayList<>();

        // 递归查找所有 @Glue 注解：先查当前元素的注解，再查注解本身的元注解
        // 只对 ANNOTATION_TYPE 类型的注解递归，避免 @Target/@Retention 等造成无限递归
        Set<String> visited = new HashSet<>();
        List<AnnotatedConstruct> stack = new ArrayList<>();
        stack.add(comp);
        while (!stack.isEmpty()) {
            AnnotatedConstruct annotated = stack.removeLast();
            for (AnnotationMirror am : annotated.getAnnotationMirrors()) {
                TypeElement annoElem = (TypeElement) processingEnv.getTypeUtils().asElement(am.getAnnotationType());
                if (annoElem == null) continue;
                String qName = annoElem.getQualifiedName().toString();

                if (qName.equals(Glue.class.getCanonicalName())) {
                    glueMirrors.add(am);
                } else if (qName.equals(Glue.Glues.class.getCanonicalName())) {
                    for (AnnotationValue val : am.getElementValues().values()) {
                        for (Object inner : (List<?>) val.getValue()) {
                            AnnotationMirror innerAm = (AnnotationMirror) inner;
                            TypeElement innerElem = (TypeElement) processingEnv.getTypeUtils().asElement(innerAm.getAnnotationType());
                            if (innerElem != null && innerElem.getQualifiedName().toString().equals(Glue.class.getCanonicalName())) {
                                glueMirrors.add(innerAm);
                            }
                        }
                    }
                }

                // 只对注解类型递归查找其元注解，且跳过已访问过的类型
                if (annoElem.getKind() == ElementKind.ANNOTATION_TYPE && visited.add(qName)) {
                    stack.add(annoElem);
                }
            }
        }

        // 逐个匹配 @Glue 的 targetType
        for (AnnotationMirror glueMirror : glueMirrors) {
            TypeMirror targetType = null;
            TypeMirror dataType = null;

            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    glueMirror.getElementValues().entrySet()) {
                String key = entry.getKey().getSimpleName().toString();
                if ("targetType".equals(key)) {
                    targetType = (TypeMirror) entry.getValue().getValue();
                } else if ("dataType".equals(key)) {
                    dataType = (TypeMirror) entry.getValue().getValue();
                }
            }

            if (targetType == null || dataType == null) continue;

            try {
                dataType = processingEnv.getTypeUtils().unboxedType(dataType);
            } catch (IllegalArgumentException ignore) {
            }

            if (processingEnv.getTypeUtils().isSubtype(compClass, targetType)) {
                String[] args = getAnnotationStringArray(glueMirror, "args");
                String getField = getAnnotationString(glueMirror, "getField", "return new $T($N, $N);");
                String getFieldToDest = getAnnotationString(glueMirror, "getFieldToDest", "return $N.set($N, $N);");
                String setField = getAnnotationString(glueMirror, "setField", "$N.get($N, $N);");
                return new FieldCodeBlock(TypeName.get(dataType), TypeName.get(compClass),
                        args, getField, getFieldToDest, setField);
            }
        }
        return null;
    }

    private static String[] getAnnotationStringArray(AnnotationMirror am, String memberName) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                am.getElementValues().entrySet()) {
            if (memberName.equals(entry.getKey().getSimpleName().toString())) {
                List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) entry.getValue().getValue();
                return values.stream().map(v -> (String) v.getValue()).toArray(String[]::new);
            }
        }
        return new String[0];
    }

    private String getAnnotationString(AnnotationMirror am, String memberName, String defaultValue) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                am.getElementValues().entrySet()) {
            if (memberName.equals(entry.getKey().getSimpleName().toString())) {
                return (String) entry.getValue().getValue();
            }
        }
        TypeElement annoElem = (TypeElement) processingEnv.getTypeUtils().asElement(am.getAnnotationType());
        if (annoElem != null) {
            for (Element enclosed : annoElem.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.METHOD
                        && memberName.equals(enclosed.getSimpleName().toString())
                        && enclosed instanceof ExecutableElement ee
                        && ee.getDefaultValue() != null) {
                    return (String) ee.getDefaultValue().getValue();
                }
            }
        }
        return defaultValue;
    }


    // ------------------- 内部数据类 -------------------

    private record FieldInfo(String name, TypeName dataType, int dataLength, TypeName filedType, String group, int constructIndex, @Nullable FieldCodeBlock code) {
    }

    private record GroupInfo(String name, TypeName dataType, List<FieldInfo> fields) {
    }

    private record GroupSpec(String name, TypeName elementType, int fieldCount,
                             String sizeConstName, String arrayFieldName,
                             FieldSpec sizeField, FieldSpec arrayField) {
    }

    private record FieldSpecs(FieldInfo info, FieldSpec offsetConst, FieldSpec sizeConst,
                              MethodSpec arrayGetter, @Nullable MethodSpec arrayGetWithDist,
                              MethodSpec arraySetter, @Nullable MethodSpec arraySetPrimitive
    ) {
    }

}