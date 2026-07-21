package yee.pltision.soa.processor;

import com.palantir.javapoet.*;
import org.jetbrains.annotations.Nullable;
import yee.pltision.soa.annotation.Field;
import yee.pltision.soa.annotation.StructElementGlue;
import yee.pltision.soa.joml.JomlGlue;

import javax.annotation.processing.*;
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
    private Map<TypeName, FieldCodeBlock> elementMap;
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        try {
            // 强制加载并初始化 JomlGlue，触发静态块
            Class<?> glueClass = Class.forName("yee.pltision.soa.joml.JomlGlue");
            // 或者直接访问静态字段，也会触发初始化
            // Field mapField = glueClass.getDeclaredField("ELEMENT_MAP");
            // mapField.get(null);
        } catch (Throwable t) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "JomlGlue initialization failed: " + t);
            t.printStackTrace(); // 通常输出到控制台，可看到堆栈
            throw new RuntimeException(t); // 可选，终止处理
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element elem : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (elem.getKind() == ElementKind.RECORD) {
                    try {
                        generateStoreForRecord((TypeElement) elem);
                    }
                    catch (Throwable t){
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, t.toString());
                    }
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
        int fieldCount = components.size();
        ClassName recordClass = ClassName.get(recordElem);

        return generateStore(groups, recordClass, packageName, simpleName, storeName);
    }

    private boolean generateStore(List<GroupInfo> groups,
                                  ClassName recordClass,
                                  String packageName,
                                  String simpleName,
                                  String storeName) {
        // 1. 构建组规格（GroupSpec）列表
        List<GroupSpec> groupSpecs = new ArrayList<>();
        for (GroupInfo group : groups) {
            String groupName = group.name();
            int fieldCount = group.fields().size();
            TypeName elementType = group.type();
            ArrayTypeName arrayType = ArrayTypeName.of(elementType);

            // 组大小常量名：groupName + "Size"
            String sizeConstName = groupName + "Size";
            FieldSpec sizeConst = FieldSpec.builder(int.class, sizeConstName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$L", fieldCount)
                    .build();

            // 数组字段名：groupName + "Array"
            String arrayFieldName = groupName + "Array";
            FieldSpec arrayField = FieldSpec.builder(arrayType, arrayFieldName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .build();

            groupSpecs.add(new GroupSpec(groupName, elementType, fieldCount, sizeConstName, arrayFieldName, sizeConst, arrayField));
        }

        // 2. 构建每个字段的规格（FieldSpecs）
        List<FieldSpecs> fieldSpecsList = new ArrayList<>();
        Map<FieldInfo, MethodSpec> fieldSetterMap = new LinkedHashMap<>();
        Map<FieldInfo, MethodSpec> fieldGetterMap = new LinkedHashMap<>();

        for (GroupSpec gSpec : groupSpecs) {
            // 找到对应的 GroupInfo 以获取字段列表
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

                // 2.1 OFFSET 常量
                String offsetConstName = fieldName.toUpperCase() + "_OFFSET";
                FieldSpec offsetConst = FieldSpec.builder(int.class, offsetConstName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", offset)
                        .build();

                // 2.2 SIZE 常量（固定为1）
                String sizeConstName = fieldName.toUpperCase() + "_SIZE";
                FieldSpec sizeConst = FieldSpec.builder(int.class, sizeConstName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", 1)
                        .build();

                // 2.3 getter 方法（使用组大小常量修正索引计算）
                MethodSpec getter = MethodSpec.methodBuilder("get" + cap)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(fieldType)
                        .addParameter(int.class, "index")
                        .addStatement("return $N[index * $N + $N]",
                                gSpec.arrayField,           // 数组字段
                                gSpec.sizeConstName,        // 组大小常量
                                offsetConstName)            // 偏移常量
                        .build();

                // 2.4 setter 方法
                MethodSpec setter = MethodSpec.methodBuilder("set" + cap)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(int.class, "index")
                        .addParameter(fieldType, fieldName)
                        .addStatement("$N[index * $N + $N] = $N",
                                gSpec.arrayField,
                                gSpec.sizeConstName,
                                offsetConstName,
                                fieldName)
                        .build();

                FieldSpecs fSpec = new FieldSpecs(field, offsetConst, sizeConst, getter, setter);
                fieldSpecsList.add(fSpec);
                fieldSetterMap.put(field, setter);
                fieldGetterMap.put(field, getter);

                offset++;
            }
        }

        // 3. 开始构建类
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(ClassName.get(packageName, storeName))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // 3.1 添加 size 字段
        classBuilder.addField(FieldSpec.builder(int.class, "size")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build());

        // 3.2 添加所有组规格的常量和数组字段
        for (GroupSpec gSpec : groupSpecs) {
            classBuilder.addField(gSpec.sizeConst);
            classBuilder.addField(gSpec.arrayField);
        }

        // 3.3 添加所有字段规格的常量和方法
        for (FieldSpecs fSpec : fieldSpecsList) {
            classBuilder.addField(fSpec.offsetConst);
            classBuilder.addField(fSpec.sizeConst);
            classBuilder.addMethod(fSpec.getter);
            classBuilder.addMethod(fSpec.setter);
        }

        // 3.4 生成每个组的 setGroup 方法
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

        // 3.5 构造函数
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "size")
                .addStatement("this.size = size");
        for (GroupSpec gSpec : groupSpecs) {
            constructor.addStatement("this.$N = new $T[size * $N]",
                    gSpec.arrayField, gSpec.elementType, gSpec.sizeConstName);
        }
        classBuilder.addMethod(constructor.build());

        // 3.6 get(int) 方法（按 constructIndex 顺序组装 Record）
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

        // 3.7 set(int, Record) 方法
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

        // 4. 写入文件
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
            for(FieldInfo field: group.fields){
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
            } catch (RuntimeException e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Error processing record component '" + comp.getSimpleName() + "':\n" + e,
                        comp
                );
                hasError = true;
            }
        }
        if(hasError){
            return Optional.empty();
        }
        return Optional.of(fields);
    }

    private String getGroupFromComponent(Element comp) {
        Field fieldAnno = comp.getAnnotation(Field.class);
        return fieldAnno != null ? fieldAnno.group() : "";
    }

    private FieldCodeBlock getElementSpecsFromComponent(RecordComponentElement comp) throws RuntimeException{
        StructElementGlue glue=getGlue(comp);
        return glue == null ? null : getFromGlue(comp.asType(), glue);
    }

    private StructElementGlue getGlue(RecordComponentElement comp) throws RuntimeException{
        List<?extends AnnotationMirror> annotations = comp.getAnnotationMirrors();

        // 记录是否有多重注解
        List<StructElementGlue> glues=new ArrayList<>();

        {
            StructElementGlue glue = comp.getAnnotation(StructElementGlue.class);
            if (glue != null) {
                glues.add(glue);
            }
        }
        for(AnnotationMirror anno: annotations){
            // 手写语法糖让注解可以组合
            StructElementGlue glue=anno.getAnnotationType().getAnnotation(StructElementGlue.class);
            if (glue != null) {
                glues.add(glue);
            }
        }
        if(glues.size()==1){
            return glues.getFirst();
        }
        if(glues.isEmpty()){
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Record component ").append(comp.getSimpleName()).append(" has multiple StructElementGlue annotations");
        for (StructElementGlue glue : glues) {
            builder.append("\n\tGlue: ").append(glue.glue().getName()).append(", mapFieldName: ").append(glue.mapFieldName());
        }
        throw new RuntimeException(builder.toString());
    }

    public static FieldCodeBlock getFromGlue(TypeMirror type, StructElementGlue structElementGlue) throws RuntimeException{
        java.lang.reflect.Field field;
        try {
            field = structElementGlue.glue().getField(structElementGlue.mapFieldName());
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Cannot find field " + structElementGlue.mapFieldName() + " in " + structElementGlue.glue().getName()+"\n"+e);
        }
        try {
            FieldCodeBlock fieldCodeBlock =((Map<TypeName, FieldCodeBlock>) field.get(null)).get(TypeName.get(type));
            if (fieldCodeBlock==null)
                throw new RuntimeException("Field " + structElementGlue.mapFieldName() + " in " + structElementGlue.glue().getName() + " does not contain a mapping for type " + type);
            return fieldCodeBlock;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access field " + structElementGlue.mapFieldName() + " in " + structElementGlue.glue().getName());
        } catch (ClassCastException e){
            throw new RuntimeException("Field " + structElementGlue.mapFieldName() + " in " + structElementGlue.glue().getName() + " is not of type Map<Class<?>, FieldCodeBlock>");
        }
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase(ENGLISH) + name.substring(1);
    }


    // 输入处理
    private record FieldInfo(String name, TypeName type, String group, int constructIndex, @Nullable FieldCodeBlock specs) {
    }

    private record GroupInfo(String name, TypeName type, List<FieldInfo> fields) {
    }

    // 输出
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
}