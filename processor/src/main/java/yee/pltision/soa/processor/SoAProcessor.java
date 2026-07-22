package yee.pltision.soa.processor;

import com.palantir.javapoet.*;
import org.jetbrains.annotations.Nullable;
import yee.pltision.soa.annotation.Field;
import yee.pltision.soa.annotation.StructElementGlue;
import yee.pltision.soa.processor.spi.ElementGlueProvider;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Locale.ENGLISH;

@SupportedAnnotationTypes("yee.pltision.soa.annotation.SoA")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class SoAProcessor extends AbstractProcessor {

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
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "Loaded glue provider: " + provider.getClass().getName());
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

        Optional<List<FieldInfo>> fieldInfosOpt = getFieldFromRecord(components);
        if (fieldInfosOpt.isEmpty()) {
            return false;
        }

        Optional<List<GroupInfo>> groupsOpt = getGroups(
                fieldInfosOpt.get(),
                simpleName.toLowerCase(Locale.ROOT)
        );
        if (groupsOpt.isEmpty()) {
            return false;
        }

        List<GroupInfo> groups = groupsOpt.get();
        ClassName recordClass = ClassName.get(recordElem);

        return generateStore(groups, recordClass, packageName, simpleName, storeName);
    }

    // ------------------- 核心生成方法（未大变） -------------------

    private boolean generateStore(List<GroupInfo> groups,
                                  ClassName recordClass,
                                  String packageName,
                                  String simpleName,
                                  String storeName) {
        // 构建数组
        List<GroupSpec> groupSpecs = new ArrayList<>();
        for (GroupInfo group : groups) {
            String groupName = group.name();
            int fieldCount = group.fields().size();
            TypeName elementType = group.type();
            ArrayTypeName arrayType = ArrayTypeName.of(elementType);

            String sizeConstName = groupName + "Size";
            FieldSpec sizeConst = FieldSpec.builder(int.class, sizeConstName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$L", fieldCount)
                    .build();

            String arrayFieldName = groupName + "Array";
            FieldSpec arrayField = FieldSpec.builder(arrayType, arrayFieldName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .build();

            groupSpecs.add(new GroupSpec(groupName, elementType, fieldCount, sizeConstName, arrayFieldName, sizeConst, arrayField));
        }

        // 构建字段 getter setter 等需要被调用的函数
        List<FieldSpecs> fieldSpecsList = new ArrayList<>();
        Map<FieldInfo, MethodSpec> fieldSetterMap = new LinkedHashMap<>();
        Map<FieldInfo, MethodSpec> fieldGetterMap = new LinkedHashMap<>();

        for (GroupSpec gSpec : groupSpecs) {
            GroupInfo groupInfo = groups.stream()
                    .filter(g -> g.name().equals(gSpec.name))
                    .findFirst()
                    .orElseThrow();
            List<FieldInfo> fields = groupInfo.fields();

            int offset = 0;
            for (FieldInfo field : fields) {
                String fieldName = field.name();
                TypeName fieldType = field.type();
                String cap = capitalize(fieldName);

                String offsetConstName = fieldName.toUpperCase() + "_OFFSET";
                FieldSpec offsetConst = FieldSpec.builder(int.class, offsetConstName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", offset)
                        .build();

                String sizeConstName = fieldName.toUpperCase() + "_SIZE";
                FieldSpec sizeConst = FieldSpec.builder(int.class, sizeConstName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", 1)
                        .build();

                MethodSpec getter = MethodSpec.methodBuilder("get" + cap)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(fieldType)
                        .addParameter(int.class, "index")
                        .addStatement("return $N[index * $N + $N]",
                                gSpec.arrayField, gSpec.sizeConstName, offsetConstName)
                        .build();

                MethodSpec setter = MethodSpec.methodBuilder("set" + cap)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(int.class, "index")
                        .addParameter(fieldType, fieldName)
                        .addStatement("$N[index * $N + $N] = $N",
                                gSpec.arrayField, gSpec.sizeConstName, offsetConstName, fieldName)
                        .build();

                FieldSpecs fSpec = new FieldSpecs(field, offsetConst, sizeConst, getter, setter);
                fieldSpecsList.add(fSpec);
                fieldSetterMap.put(field, setter);
                fieldGetterMap.put(field, getter);

                offset++;
            }
        }

        // 构建类
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(ClassName.get(packageName, storeName))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(FieldSpec.builder(int.class, "size")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build());

        for (GroupSpec gSpec : groupSpecs) {
            classBuilder.addField(gSpec.sizeConst);
            classBuilder.addField(gSpec.arrayField);
        }

        for (FieldSpecs fSpec : fieldSpecsList) {
            classBuilder.addField(fSpec.offsetConst);
            classBuilder.addField(fSpec.sizeConst);
            classBuilder.addMethod(fSpec.getter);
            classBuilder.addMethod(fSpec.setter);
        }

        for (GroupSpec gSpec : groupSpecs) {
            GroupInfo groupInfo = groups.stream()
                    .filter(g -> g.name().equals(gSpec.name))
                    .findFirst()
                    .orElseThrow();
            List<FieldInfo> fields = groupInfo.fields();

            MethodSpec.Builder groupSetter = MethodSpec.methodBuilder("set" + capitalize(gSpec.name))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, "index");
            for (FieldInfo field : fields) {
                groupSetter.addParameter(field.type(), field.name());
            }
            CodeBlock.Builder body = CodeBlock.builder();
            for (FieldInfo field : fields) {
                body.addStatement("$N(index, $N)", fieldSetterMap.get(field), field.name());
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

        // get(int)
        List<FieldInfo> allFields = groups.stream()
                .flatMap(g -> g.fields().stream())
                .sorted(Comparator.comparingInt(FieldInfo::constructIndex))
                .collect(Collectors.toList());

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
            getBody.append("$N(index)");
            getArgs.add(fieldGetterMap.get(field));
        }
        getBody.append(");");
        getElement.addCode(getBody.toString(), getArgs.toArray());
        classBuilder.addMethod(getElement.build());

        // set(int, Record)
        MethodSpec.Builder setElement = MethodSpec.methodBuilder("set")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "index")
                .addParameter(recordClass, simpleName.toLowerCase(ENGLISH));
        String recordParam = simpleName.toLowerCase(ENGLISH);
        for (FieldInfo field : allFields) {
            setElement.addStatement("$N(index, $N.$N())",
                    fieldSetterMap.get(field), recordParam, field.name());
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
                    g -> new GroupInfo(g, field.type(), new ArrayList<>()));
            if (!group.type().equals(field.type())) {
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
                error.append("\t").append(field.type().toString())
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
                FieldCodeBlock fieldCodeBlock = getElementSpecsFromComponent(comp);
                TypeName type;
                if (fieldCodeBlock != null) {
                    type = fieldCodeBlock.type();
                } else {
                    type = TypeName.get(comp.asType());
                }
                String group = getGroupFromComponent(comp);
                fields.add(new FieldInfo(name, type, group, i, fieldCodeBlock));
                i++;
            } catch (Throwable t) {   // 捕获所有异常，防止单个组件问题导致全盘崩溃
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

    private FieldCodeBlock getElementSpecsFromComponent(RecordComponentElement comp) throws RuntimeException {
        GlueInfo glueInfo = getGlue(comp);
        if (glueInfo == null) return null;
        return getFromGlue(comp.asType(), glueInfo);
    }

    /**
     * 通过 {@link AnnotationMirror} 提取 {@code StructElementGlue} 注解的属性。
     * 避免直接调用 {@code glue()} 方法，防止 {@link javax.lang.model.type.MirroredTypeException}。
     */
    private GlueInfo getGlue(RecordComponentElement comp) throws RuntimeException {
        List<GlueInfo> infos = new ArrayList<>();
        for (AnnotationMirror am : comp.getAnnotationMirrors()) {
            TypeMirror annoType = am.getAnnotationType();
            TypeElement annoElement = (TypeElement) processingEnv.getTypeUtils().asElement(annoType);
            if (annoElement.getQualifiedName().toString()
                    .equals(StructElementGlue.class.getCanonicalName())) {
                String mapFieldName = null;
                TypeMirror glueTypeMirror = null;
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                        am.getElementValues().entrySet()) {
                    String key = entry.getKey().getSimpleName().toString();
                    if ("mapFieldName".equals(key)) {
                        mapFieldName = (String) entry.getValue().getValue();
                    } else if ("glue".equals(key)) {
                        glueTypeMirror = (TypeMirror) entry.getValue().getValue();
                    }
                }
                if (mapFieldName != null && glueTypeMirror != null) {
                    infos.add(new GlueInfo(mapFieldName, glueTypeMirror));
                }
            }
        }

        // 也检查直接注解（以防 AnnotationMirrors 中未包含，但通常会被包含，不过为了安全）
        StructElementGlue direct = comp.getAnnotation(StructElementGlue.class);
        if (direct != null && infos.isEmpty()) {
            // 理论不会到这里，因为直接注解也会出现在 AnnotationMirrors 中
            // 但为了防御，我们仍尝试通过反射获取 mapFieldName（但 glue 无法获取）
            // 所以忽略此情况，报错
            throw new RuntimeException("Direct @StructElementGlue found but no AnnotationMirror entry?");
        }

        if (infos.isEmpty()) {
            return null;
        }
        if (infos.size() > 1) {
            throw new RuntimeException("Multiple StructElementGlue annotations found on component " +
                    comp.getSimpleName());
        }
        return infos.get(0);
    }

    /**
     * 根据 glue 类型查找对应的 {@link ElementGlueProvider}，再从 provider 中查询字段类型的映射。
     */
    private FieldCodeBlock getFromGlue(TypeMirror fieldType, GlueInfo glueInfo) throws RuntimeException {
        if (providerMap == null || providerMap.isEmpty()) {
            throw new RuntimeException("No glue providers available. " +
                    "Make sure you have added the corresponding glue library (e.g., JomlGlue) " +
                    "to the annotation processor classpath.");
        }

        // 获取 glue 类的全限定名
        String glueClassName = ((TypeElement) processingEnv.getTypeUtils().asElement(glueInfo.glueType()))
                .getQualifiedName().toString();

        // 在 providerMap 中查找匹配的 provider
        ElementGlueProvider provider = null;
        for (Map.Entry<TypeMirror, ElementGlueProvider> entry : providerMap.entrySet()) {
            TypeElement providerElement = (TypeElement) processingEnv.getTypeUtils().asElement(entry.getKey());
            if (providerElement.getQualifiedName().toString().equals(glueClassName)) {
                provider = entry.getValue();
                break;
            }
        }

        if (provider == null) {
            throw new RuntimeException("No provider found for glue class: " + glueClassName +
                    ". Please include the corresponding glue library and ensure it is registered via ServiceLoader.");
        }

        Map<TypeName, FieldCodeBlock> glueMap = provider.getElementMap();
        if (glueMap == null) {
            throw new RuntimeException("Provider " + glueClassName + " returned null map");
        }

        TypeName key = TypeName.get(fieldType);
        FieldCodeBlock block = glueMap.get(key);
        if (block == null) {
            throw new RuntimeException("No mapping for type " + fieldType +
                    " in glue " + glueClassName + ". Supported types: " + glueMap.keySet());
        }
        return block;
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }

    // ------------------- 内部数据类 -------------------

    private record FieldInfo(String name, TypeName type, String group, int constructIndex, @Nullable FieldCodeBlock specs) {
    }

    private record GroupInfo(String name, TypeName type, List<FieldInfo> fields) {
    }

    private record GroupSpec(String name, TypeName elementType, int fieldCount,
                             String sizeConstName, String arrayFieldName,
                             FieldSpec sizeConst, FieldSpec arrayField) {
    }

    private record FieldSpecs(FieldInfo info,
                              FieldSpec offsetConst,
                              FieldSpec sizeConst,
                              MethodSpec getter,
                              MethodSpec setter) {
    }

    private record GlueInfo(String mapFieldName, TypeMirror glueType) {
    }
}