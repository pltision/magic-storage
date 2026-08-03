package yee.pltision.soa.processor;

import cn.hutool.core.text.NamingCase;
import com.palantir.javapoet.*;
import yee.pltision.soa.annotation.Field;
import yee.pltision.soa.annotation.Glue;
import yee.pltision.soa.annotation.SoA;
import yee.pltision.soa.compoundsource.MutableClassSource;


import yee.pltision.soa.processor.step.src.*;
import yee.pltision.soa.processor.step.res.*;

import javax.annotation.processing.*;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@SupportedAnnotationTypes("yee.pltision.soa.annotation.SoA")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class SoAProcessor extends AbstractProcessor {

    public static final ClassName ANNOTATION_CLASS_NAME = ClassName.get(SoA.class);

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
                            "@" + ANNOTATION_CLASS_NAME.simpleName() + " can only be used on Record or Class", elem);
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
                    "No record components found for @" + ANNOTATION_CLASS_NAME.simpleName(), recordElem);
            return false;
        }

        Optional<List<FieldSource>> fieldInfosOpt = getFieldFromRecord(components);
        if (fieldInfosOpt.isEmpty()) {
            return false;
        }

        Optional<List<GroupSource>> groupsOpt = getGroups(
                fieldInfosOpt.get(),
                NamingCase.toCamelCase(simpleName)
        );
        if (groupsOpt.isEmpty()) {
            return false;
        }

        List<GroupSource> groups = groupsOpt.get();
        ClassName recordClass = ClassName.get(recordElem);

        return generateStore(groups, recordClass, packageName, simpleName, storeName);
    }

    // ------------------- 核心生成方法 -------------------

    private boolean generateStore(List<GroupSource> groups,
                                  ClassName recordClass,
                                  String packageName,
                                  String simpleName,
                                  String storeName) {
        // 构建数组
        List<GroupResult> groupSpecs = new ArrayList<>();
        for (GroupSource group : groups) {
            String groupName = group.name();

            //直接数，field和group其实相互依赖，但group可以暂时全用标量
            int sizeCount = 0;
            for(FieldSource field: group.fields()){
                sizeCount+=field.dataLength();
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

            groupSpecs.add(new GroupResult(groupName, elementType, sizeCount, sizeConstName, arrayFieldName, sizeConst, arrayField));
        }

        String indexName = "elementIndex";

        // 构建字段 arrayGetter arraySetter() 等需要被调用的函数
        List<FieldResult> fieldSpecsList = new ArrayList<>();

        for (GroupResult gSpec : groupSpecs) {
            GroupSource groupSource = groups.stream()
                    .filter(g -> g.name().equals(gSpec.name()))
                    .findFirst()
                    .orElseThrow();
            List<FieldSource> fields = groupSource.fields();

            int offset = 0;
            for (FieldSource field : fields) {
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
                        .initializer("$L", field.dataLength())
                        .build();

                // 分裂胶水和原初类型

                if(field.code() ==null){
                    // float getField(int index)
                    MethodSpec getter = MethodSpec.methodBuilder("get" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .returns(fieldType)
                            .addStatement("return $N[$N * $N + $N]",
                                    gSpec.arrayField(), indexName, gSpec.sizeConstName(), offsetConstName)
                            .build();

                    // float setField(int index, float f)
                    MethodSpec setter = MethodSpec.methodBuilder("set" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .addParameter(fieldType, fieldName)
                            .addStatement("$N[$N * $N + $N] = $N",
                                    gSpec.arrayField(), indexName, gSpec.sizeConstName(), offsetConstName, fieldName)
                            .build();

                    FieldResult fSpec = new FieldResult(field, offsetField, sizeConst,
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
                            .addCode(field.code().getField(), field.filedType(), gSpec.arrayField(),
                                    CodeBlock.of("($N * $N + $N)", indexName, gSpec.sizeField(), offsetConstName).toString())
                            .build();

                    String destName="dest";
                    // F getField(int index, F dest)
                    MethodSpec getWithDist = MethodSpec.methodBuilder("get" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .addParameter(fieldType, destName)
                            .returns(fieldType)
                            .addCode(field.code().getFieldToDest(), destName, gSpec.arrayField(),
                                    CodeBlock.of("($N * $N + $N)", indexName, gSpec.sizeField(), offsetConstName).toString())
                            .build();

                    // F setField(int index, F field)
                    MethodSpec setter = MethodSpec.methodBuilder("set" + capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName)
                            .addParameter(fieldType, fieldName)
                            .addCode(field.code().setField(),
                                    fieldName,
                                    gSpec.arrayField(),
                                    CodeBlock.of("($N * $N + $N)", indexName, gSpec.sizeField(), offsetConstName).toString())
                            .build();

                    // F setField(int index, float... data)
                    MethodSpec.Builder setByPrimitive = MethodSpec.methodBuilder("set"+capFileName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(int.class, indexName);

                    String[] args = field.code().args();
                    for(int i=0;i<args.length;i++){
                        setByPrimitive.addParameter(field.dataType(), args[i]);
                        setByPrimitive.addStatement("$N[$N * $N + $N + $L] = $N",
                                gSpec.arrayField(),
                                gSpec.sizeField(), indexName, offsetField, i,
                                args[i]
                        );
                    }

                    FieldResult fSpec = new FieldResult(field, offsetField, sizeConst,
                            getter,getWithDist,
                            setter, setByPrimitive.build()
                    );
                    fieldSpecsList.add(fSpec);
                }


                offset+=field.dataLength();
            }
        }

        // 构建类
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(ClassName.get(packageName, storeName))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Generate by $T with @$N\n",
                        ClassName.get(packageName,simpleName), ANNOTATION_CLASS_NAME.simpleName()
                )
                .addJavadoc("@see $N\n", ANNOTATION_CLASS_NAME.toString())    //显示全名
                .addJavadoc("@see $T\n", ClassName.get(packageName,simpleName))
                ;

        classBuilder.addField(FieldSpec.builder(int.class, "size")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build());

        for (GroupResult gSpec : groupSpecs) {
            classBuilder.addField(gSpec.sizeField());
            classBuilder.addField(gSpec.arrayField());
        }

        for (FieldResult fSpec : fieldSpecsList) {
            classBuilder.addField(fSpec.offsetConst());
            classBuilder.addField(fSpec.sizeConst());
            classBuilder.addMethod(fSpec.arrayGetter());
            classBuilder.addMethod(fSpec.arraySetter());
            if(fSpec.arrayGetWithDist()!=null) classBuilder.addMethod(fSpec.arrayGetWithDist());
            if(fSpec.arraySetPrimitive()!=null) classBuilder.addMethod(fSpec.arraySetPrimitive());
        }

        // setGroup(int index, ... data)
        for (GroupResult gSpec : groupSpecs) {
            GroupSource groupSource = groups.stream()
                    .filter(g -> g.name().equals(gSpec.name()))
                    .findFirst()
                    .orElseThrow();
            List<FieldSource> fields = groupSource.fields();

            MethodSpec.Builder groupSetter = MethodSpec.methodBuilder("set" + NamingCase.toPascalCase(gSpec.name()))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, indexName);
            for (FieldSource field : fields) {
                groupSetter.addParameter(field.filedType(), field.name());
            }
            CodeBlock.Builder body = CodeBlock.builder();
            for (FieldSource field : fields) {
                //通过constructIndex获取field，其实我觉得放group里面合适，但反正能用
                body.addStatement("$N($N, $N)",
                        fieldSpecsList.get(field.constructIndex()).arraySetter(),
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
        for (GroupResult gSpec : groupSpecs) {
            constructor.addStatement("this.$N = new $T[size * $N]",
                    gSpec.arrayField(), gSpec.elementType(), gSpec.sizeConstName());
        }
        classBuilder.addMethod(constructor.build());

        // Element getElement(int index)
        List<FieldSource> allFields = groups.stream()
                .flatMap(g -> g.fields().stream())
                .sorted(Comparator.comparingInt(FieldSource::constructIndex))
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
            FieldSource field = allFields.get(i);
            getBody.append("$N($N)");
            getArgs.add(fieldSpecsList.get(field.constructIndex()).arrayGetter());
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
        for (FieldSource field : allFields) {
            setElement.addStatement("$N($N, $N.$N())",
                    fieldSpecsList.get(field.constructIndex()).arraySetter(),
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

    private Optional<List<GroupSource>> getGroups(List<FieldSource> fieldInfos, String defaultGroup) {
        Map<String, GroupSource> groupMap = new LinkedHashMap<>();
        Set<GroupSource> multipleTypeGroups = new HashSet<>();

        for (FieldSource field : fieldInfos) {
            String groupName = field.group().isEmpty() ? defaultGroup : field.group();
            GroupSource group = groupMap.computeIfAbsent(groupName,
                    g -> new GroupSource(g, field.dataType(), new ArrayList<>()));
            if (!group.dataType().equals(field.dataType())) {
                multipleTypeGroups.add(group);
            }
            group.fields().add(field);
        }

        if (multipleTypeGroups.isEmpty()) {
            return Optional.of(new ArrayList<>(groupMap.values()));
        }

        for (GroupSource group : multipleTypeGroups) {
            StringBuilder error = new StringBuilder("Group " + group.name() + " has multiple types: \n");
            for (FieldSource field : group.fields()) {
                error.append("\t").append(field.dataType().toString())
                        .append(" ").append(field.name()).append(";\n");
            }
//            error.append("");
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error.toString());
        }
        return Optional.empty();
    }

    private Optional<List<FieldSource>> getFieldFromRecord(List<? extends RecordComponentElement> components) {
        List<FieldSource> fields = new ArrayList<>();
        int i = 0;
        boolean hasError = false;
        for (RecordComponentElement comp : components) {
            try {
                String name = comp.getSimpleName().toString();
                CompoundFieldSource fieldSource = getCodeBlockFromGlue(comp, comp.asType());
                if (fieldSource == null) {
                    fieldSource = getCodeBlockFromMutableClassSource(comp.asType());
                }
                TypeName dataType;
                int dataLength;
                TypeName fieldType;
                if (fieldSource == null) {
                    dataType = TypeName.get(comp.asType());
                    fieldType = dataType;
                    dataLength = 1;
                } else {
                    dataType = fieldSource.dataType();
                    fieldType = fieldSource.fliedType();
                    //args就是标量
                    dataLength = fieldSource.args().length;
                }
                String group = getGroupFromComponent(comp);
                fields.add(new FieldSource(name, dataType, dataLength, fieldType, group, i, fieldSource));
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

    private CompoundFieldSource getCodeBlockFromGlue(AnnotatedConstruct comp, TypeMirror compType) throws RuntimeException {
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
                } else if ("dataType()".equals(key)) {
                    dataType = (TypeMirror) entry.getValue().getValue();
                }
            }

            if (targetType == null || dataType == null) continue;

            try {
                dataType = processingEnv.getTypeUtils().unboxedType(dataType);
            } catch (IllegalArgumentException ignore) {
            }

            if (processingEnv.getTypeUtils().isSubtype(compType, targetType)) {
                String[] args = getAnnotationStringArray(glueMirror, "args");
                String getField = getAnnotationString(glueMirror, "getField", "return new $T($N, $N);");
                String getFieldToDest = getAnnotationString(glueMirror, "getFieldToDest", "return $N.set($N, $N);");
                String setField = getAnnotationString(glueMirror, "setField", "$N.get($N, $N);");
                return new CompoundFieldSource(TypeName.get(dataType), TypeName.get(compType),
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

    private CompoundFieldSource getCodeBlockFromMutableClassSource(TypeMirror type){
        if(type.getKind() != TypeKind.DECLARED)
            return null;

        if(((DeclaredType)type).asElement().getAnnotation(MutableClassSource.class)==null)
            return null;



        List<TypeElement> inheritances = new ArrayList<>();
        {
            TypeElement clazz = (TypeElement) ((DeclaredType)type).asElement();
            inheritances.add(clazz);
            w:
            while (true) {
                TypeMirror e = clazz.getSuperclass();

                switch (e.getKind()) {
                    case DECLARED -> inheritances.add(clazz = (TypeElement) ((DeclaredType) e).asElement());
                    case NONE -> {break w;}
                    default -> throw new IllegalArgumentException();
                }

            }
        }

        //允许继承应该没啥问题

//        if(inheritances.size()!=2){
//            processingEnv.getMessager().printMessage(
//                    Diagnostic.Kind.ERROR,
//                    "@"+MutableClassSource.class.getSimpleName()+" "+type+" can only extends "+Object.class+"!"
//            );
//        }

        List<VariableElement> elements=new ArrayList<>();

        for(int i=inheritances.size()-1; i>=0; i--){
            TypeElement clazz = inheritances.get(i);
            elements.addAll(
                    clazz.getEnclosedElements().stream()
                            .filter(e->e.getKind()==ElementKind.FIELD)
                            .map(e->(VariableElement) e)
                            .filter(e->
                                    e.getModifiers().contains(Modifier.PUBLIC)
                               && ! e.getModifiers().contains(Modifier.FINAL)
                            )
                            .toList()
            );
        }


        // 但是不处理套娃了

        if(elements.isEmpty()){
            throw new RuntimeException("Not found any public and not final field in @"+MutableClassSource.class.getSimpleName()+" "+type+" and their super class");
        }

        List<String> args=new ArrayList<>(elements.size());
        TypeMirror fieldType = elements.getFirst().asType();

        elements.forEach(e->{
            args.add(String.valueOf(e.getSimpleName()));
            if(!e.asType().equals(fieldType))
                throw  getCodeBlockFromMutableClassSourceMultipleTypeException(elements);
        });

        return new CompoundFieldSource(
                TypeName.get(fieldType),
                TypeName.get(type),
                args.toArray(new String[0]),
                genGetField(args),
                genGetFieldFromDist(args),
                genSetField(args)
        );
    }

    public String genGetField(List<String> args){
        String name = "compoundSource";
        StringBuilder builder = new StringBuilder();
        builder.append("$1T ").append(name).append(" = new $1T();\n");

        int i=0;
        for(String field:args){
            builder.append(name).append('.').append(field)
                    .append(" = $2N[$3L + ").append(i++).append("];\n");
        }

        builder.append("return ").append(name).append(";\n");

        return builder.toString();
    }

    public String genGetFieldFromDist(List<String> args){
        StringBuilder builder = new StringBuilder();

        int i=0;
        for(String field:args){
            builder.append("$1N.").append(field)
                    .append(" = $2N[$3L + ").append(i++).append("];\n");
        }

        builder.append("return $1N;\n");

        return builder.toString();
    }

    public String genSetField(List<String> args){
        StringBuilder builder = new StringBuilder();

        int i=0;
        for(String field:args){
            builder.append("$2N[$3L + ").append(i++)
                    .append("] = $1N.").append(field).append(";\n");
        }

        return builder.toString();
    }


    public RuntimeException getCodeBlockFromMutableClassSourceMultipleTypeException(List<VariableElement> elements){
        StringBuilder error = new StringBuilder(
                "@" + MutableClassSource.class.getSimpleName() + " fields must have the same type, but found: {\n");
        for (VariableElement e : elements) {
            error.append("\t").append(e.asType()).append(" ").append(e.getSimpleName()).append(";\n");
        }
        error.append("}");
        return new RuntimeException(error.toString());
    }




}