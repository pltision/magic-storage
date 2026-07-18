package yee.pltision.soa.processor;

import com.palantir.javapoet.*;
import yee.pltision.soa.annotation.Field;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

//@SupportedAnnotationTypes("yee.pltision.soa.annotation.SoA")
@SupportedSourceVersion(SourceVersion.RELEASE_17) // Record 需要 Java 14+
public class SoAProcessorAIGC extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "SoAProcessor is running!");
        if (annotations.isEmpty()) {
            return false;
        }
        for (TypeElement annotation : annotations) {
            for (Element elem : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (elem.getKind() == ElementKind.RECORD) {
                    generateStoreForRecord((TypeElement) elem);
                } else if (elem.getKind() == ElementKind.CLASS) {
                    generateStoreForClass((TypeElement) elem);
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@SoA can only be used on Record or Class", elem);
                }
            }
        }
        return true;
    }

    // ---------- 处理 Record ----------
    private void generateStoreForRecord(TypeElement recordElem) {
        String packageName = processingEnv.getElementUtils().getPackageOf(recordElem).getQualifiedName().toString();
        String simpleName = recordElem.getSimpleName().toString();
        String storeName = simpleName + "Store";

        List<? extends RecordComponentElement> components = recordElem.getRecordComponents();
        if (components.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "No record components found for @SoA", recordElem);
            return;
        }

        // 构建 groupMap：按 group 分组，未指定 group 的字段使用字段名作为独立组
        Map<String, List<FieldInfo>> groupMap = new LinkedHashMap<>();
        for (RecordComponentElement comp : components) {
            String fieldName = comp.getSimpleName().toString();
            TypeMirror type = comp.asType();
            String group = getGroupFromComponent(comp);
            // 如果未指定 group，则使用字段名作为组名（每个字段独立成组）
            if (group.isEmpty()) {
                group = fieldName;
            }
            groupMap.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(new FieldInfo(fieldName, type, group));
        }

        generateStore(packageName, storeName, groupMap);
    }

    // ---------- 处理普通 Class（通过字段提取）----------
    private void generateStoreForClass(TypeElement classElem) {
        String packageName = processingEnv.getElementUtils().getPackageOf(classElem).getQualifiedName().toString();
        String simpleName = classElem.getSimpleName().toString();
        String storeName = simpleName + "Store";

        List<VariableElement> fields = classElem.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .map(e -> (VariableElement) e)
                .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                .collect(Collectors.toList());

        if (fields.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "No instance fields found for @SoA", classElem);
            return;
        }

        // 同样按 group 分组，未指定 group 的字段使用字段名作为独立组
        Map<String, List<FieldInfo>> groupMap = new LinkedHashMap<>();
        for (VariableElement field : fields) {
            String fieldName = field.getSimpleName().toString();
            TypeMirror type = field.asType();
            // 普通 Class 的字段暂不支持 @Field，此处默认为空，我们直接用字段名做组名
            String group = fieldName; // 每个字段独立成组
            groupMap.computeIfAbsent(group, k -> new ArrayList<>())
                    .add(new FieldInfo(fieldName, type, group));
        }

        generateStore(packageName, storeName, groupMap);
    }

    // ---------- 公共生成逻辑 ----------
    private void generateStore(String packageName, String storeName,
                               Map<String, List<FieldInfo>> groupMap) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(storeName)
                .addModifiers(Modifier.PUBLIC);

        // 1. 为每个 group 生成一个交错数组（例如：positionData, colorData, xData...）
        //    并添加对应的 getXxxData() 方法
        for (Map.Entry<String, List<FieldInfo>> entry : groupMap.entrySet()) {
            String groupName = entry.getKey();
            List<FieldInfo> groupFields = entry.getValue();

            // 检查同一组内字段类型是否一致（交错数组要求类型一致）
            TypeName commonType = TypeName.get(groupFields.get(0).type);
            boolean allSame = groupFields.stream()
                    .allMatch(f -> TypeName.get(f.type).equals(commonType));
            if (!allSame) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Fields in group '" + groupName + "' must have same type for vertex data");
                return;
            }

            String arrayFieldName = groupName + "Data";
            classBuilder.addField(ArrayTypeName.of(commonType), arrayFieldName, Modifier.PRIVATE);

            // getXxxData()
            classBuilder.addMethod(MethodSpec.methodBuilder("get" + capitalize(groupName) + "Data")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ArrayTypeName.of(commonType))
                    .addStatement("return this.$L", arrayFieldName)
                    .build());
        }

        // 2. size 字段
        classBuilder.addField(int.class, "size", Modifier.PRIVATE);

        // 3. 构造方法
        MethodSpec.Builder ctor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, "capacity");
        for (Map.Entry<String, List<FieldInfo>> entry : groupMap.entrySet()) {
            String groupName = entry.getKey();
            List<FieldInfo> groupFields = entry.getValue();
            TypeName commonType = TypeName.get(groupFields.get(0).type);
            ctor.addStatement("this.$LData = new $T[capacity * $L]",
                    groupName, commonType, groupFields.size());
        }
        ctor.addStatement("this.size = 0");
        classBuilder.addMethod(ctor.build());

        // 4. add 方法：接收所有字段，按组交错存入
        MethodSpec.Builder addMethod = MethodSpec.methodBuilder("add")
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class);

        // 收集所有字段作为参数
        List<FieldInfo> allFields = groupMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        for (FieldInfo f : allFields) {
            addMethod.addParameter(TypeName.get(f.type), f.name);
        }

        // add 方法体
        for (Map.Entry<String, List<FieldInfo>> entry : groupMap.entrySet()) {
            String groupName = entry.getKey();
            List<FieldInfo> groupFields = entry.getValue();
            String arrayField = groupName + "Data";
            int stride = groupFields.size();
            int offset = 0;
            for (FieldInfo f : groupFields) {
                addMethod.addStatement("this.$L[$L * $L + $L] = $L",
                        arrayField, "size", stride, offset, f.name);
                offset++;
            }
        }
        addMethod.addStatement("size++");
        classBuilder.addMethod(addMethod.build());

        // 5. size()
        classBuilder.addMethod(MethodSpec.methodBuilder("size")
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return size")
                .build());

        // 6. 按组生成 forEach 迭代方法
        for (Map.Entry<String, List<FieldInfo>> entry : groupMap.entrySet()) {
            String groupName = entry.getKey();
            List<FieldInfo> groupFields = entry.getValue();
            String arrayField = groupName + "Data";
            int stride = groupFields.size();

            // 生成内部 Consumer 接口
            String consumerName = capitalize(groupName) + "Consumer";
            MethodSpec.Builder consumerMethod = MethodSpec.methodBuilder("accept")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
            for (FieldInfo f : groupFields) {
                consumerMethod.addParameter(TypeName.get(f.type), f.name);
            }
            classBuilder.addType(TypeSpec.interfaceBuilder(consumerName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addMethod(consumerMethod.build())
                    .build());

            // 生成 forEachXxx 方法
            MethodSpec.Builder forEachMethod = MethodSpec.methodBuilder("forEach" + capitalize(groupName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void.class)
                    .addParameter(ClassName.get("", consumerName), "action");
            forEachMethod.beginControlFlow("for (int i = 0; i < size; i++)");
            StringBuilder call = new StringBuilder("action.accept(");
            for (int i = 0; i < stride; i++) {
                if (i > 0) call.append(", ");
                call.append("this.").append(arrayField).append("[i * ").append(stride).append(" + ").append(i).append("]");
            }
            call.append(")");
            forEachMethod.addStatement(call.toString());
            forEachMethod.endControlFlow();
            classBuilder.addMethod(forEachMethod.build());
        }

        // 生成 Java 文件
        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate store: " + e.getMessage());
        }
    }

    // ---------- 辅助方法 ----------
    private String getGroupFromComponent(RecordComponentElement comp) {
        Field fieldAnno = comp.getAnnotation(Field.class);
        if (fieldAnno != null) {
            return fieldAnno.group();
        }
        return "";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- 内部数据类 ----------
    private static class FieldInfo {
        String name;
        TypeMirror type;
        String group; // 所属组名
        FieldInfo(String name, TypeMirror type, String group) {
            this.name = name;
            this.type = type;
            this.group = group;
        }
    }
}