/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package yee.pltision.magicstorage.processor;

import cn.hutool.core.text.NamingCase;
import com.palantir.javapoet.*;
import yee.pltision.magicstorage.StringUtil;
import yee.pltision.magicstorage.annotation.Field;
import yee.pltision.magicstorage.annotation.GenStore;
import yee.pltision.magicstorage.annotation.Glue;
import yee.pltision.magicstorage.compoundsource.MutableClassSource;


import yee.pltision.magicstorage.processor.step.src.*;
import yee.pltision.magicstorage.processor.step.res.*;

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


@SupportedAnnotationTypes("yee.pltision.magicstorage.annotation.GenStore")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class StoreProcessor extends AbstractProcessor {

    public static final ClassName ANNOTATION_CLASS_NAME = ClassName.get(GenStore.class);

    public static final String indexName="elementIndex";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean anySuccess=false;
        for (TypeElement annotation : annotations) {
            for (Element elem : roundEnv.getElementsAnnotatedWith(annotation)) {
                try {
                    if (elem.getKind() == ElementKind.RECORD) {
                        anySuccess |= generateStoreForRecord((TypeElement) elem);
                    } else if (elem.getKind() == ElementKind.CLASS) {
                        anySuccess |= generateStoreForClass((TypeElement) elem);
                    } else {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                "@" + ANNOTATION_CLASS_NAME.simpleName() + " can only be used on Record or Class", elem);
                    }
                }
                catch (Throwable t){
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, t.toString());
                    t.printStackTrace();
                }
            }
        }
        return anySuccess;
    }

    private boolean generateStoreForRecord(TypeElement element) {
        String packageName = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
        String simpleName = element.getSimpleName().toString();
        String storeName = simpleName + "Store";

        List<? extends RecordComponentElement> components = element.getRecordComponents();
        if (components.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "No record components found for @" + ANNOTATION_CLASS_NAME.simpleName(), element);
            return false;
        }

        Optional<List<FieldSource>> fieldInfosOpt = getFieldFromRecord(components);
        if (fieldInfosOpt.isEmpty()) {
            return false;
        }

        Optional<List<GroupSource>> groupsOpt = getGroups(
                fieldInfosOpt.get(),
                StringUtil.firstLower(simpleName)
        );
        if (groupsOpt.isEmpty()) {
            return false;
        }

        ClassName recordClass = ClassName.get(element);
        ClassName storeClass = ClassName.get(packageName, storeName);

        return generateStore(
                new ElementSource(recordClass, storeClass, ElementSource.fullConstructor(fieldInfosOpt.get()),null)
                , groupsOpt.get(), fieldInfosOpt.get());
    }

    private boolean generateStoreForClass(TypeElement element) {
        String packageName = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
        String simpleName = element.getSimpleName().toString();
        String storeName = simpleName + "Store";

        List<? extends VariableElement> components = element.getEnclosedElements().stream()
                .filter(e->e.getKind()==ElementKind.FIELD)
                .map(e->(VariableElement) e)
                .filter(e->
                        e.getModifiers().contains(Modifier.PUBLIC)
                                && ! e.getModifiers().contains(Modifier.FINAL)
                )
                .toList();

        if (components.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "No public and not static field found for @" + ANNOTATION_CLASS_NAME.simpleName(), element);
            return false;
        }

        Optional<List<FieldSource>> fieldInfosOpt = getFieldFromClass(components);
        if (fieldInfosOpt.isEmpty()) {
            return false;
        }

        Optional<List<GroupSource>> groupsOpt = getGroups(
                fieldInfosOpt.get(),
                StringUtil.firstLower(simpleName)
        );
        if (groupsOpt.isEmpty()) {
            return false;
        }

        ClassName recordClass = ClassName.get(element);
        ClassName storeClass = ClassName.get(packageName, storeName);

        return generateStore(
                new ElementSource(recordClass, storeClass, ElementSource.emptyConstructor(fieldInfosOpt.get(), storeClass),null)
                , groupsOpt.get(), fieldInfosOpt.get());
    }

    // ------------------- 核心生成方法 -------------------

    private boolean generateStore(
            ElementSource store,
            List<GroupSource> groups,
            List<FieldSource> fields
    ) {
        // 构建数组
        List<GroupResult> groupResults = new ArrayList<>();
        for (GroupSource group : groups) {
            groupResults.add(GroupResult.gen(group));
        }

        String indexName = "elementIndex";

        // 构建字段 arrayGetter arraySetter() 等需要被调用的函数
        FieldResult[] fieldResult = new FieldResult[fields.size()];

        for (GroupResult gSpec : groupResults) {
            GroupSource groupSource = groups.stream()
                    .filter(g -> g.name().equals(gSpec.name()))
                    .findFirst()
                    .orElseThrow();
            List<FieldSource> groupFields = groupSource.fields();

            int offset = 0;
            for (FieldSource field : groupFields) {
                fieldResult[field.constructIndex()]=FieldResult.gen(gSpec,field,offset);
                offset+=field.dataLength();
            }
        }

        // 构建类
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(store.storeName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addJavadoc("Generate by $T with @$N\n",
                        store.elementName(), ANNOTATION_CLASS_NAME.simpleName()
                )
                .addJavadoc("@see $N\n", ANNOTATION_CLASS_NAME.toString())    //显示全名
                .addJavadoc("@see $T\n", store.elementName())
                ;

        FieldSpec sizeField=FieldSpec.builder(int.class, "size")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build();

        classBuilder.addField(sizeField);


        // 添加 group

        for (GroupResult gSpec : groupResults) {
            classBuilder.addField(gSpec.sizeField());
            classBuilder.addField(gSpec.arrayField());
        }

        // 添加 field

        for (FieldResult fSpec : fieldResult) {
            classBuilder.addField(fSpec.offsetConst());
            classBuilder.addField(fSpec.sizeConst());
            classBuilder.addMethod(fSpec.arrayGetter());
            classBuilder.addMethod(fSpec.arraySetter());
            if(fSpec.arrayGetWithDist()!=null) classBuilder.addMethod(fSpec.arrayGetWithDist());
            if(fSpec.arraySetPrimitive()!=null) classBuilder.addMethod(fSpec.arraySetPrimitive());
        }

        // 添加store

        // setGroup(int index, ... data)
        for (GroupResult groupResult : groupResults) {

            // 跳过与 field 重名的 group
            if(!groupResult.genGroupAccessors())
                continue;

            GroupSource groupSource = groups.stream()
                    .filter(g -> g.name().equals(groupResult.name()))
                    .findFirst()
                    .orElseThrow();
            List<FieldSource> groupFields = groupSource.fields();

            MethodSpec.Builder groupSetter = MethodSpec.methodBuilder("set" + NamingCase.toPascalCase(groupResult.name()))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, indexName);
            for (FieldSource field : groupFields) {
                groupSetter.addParameter(field.filedType(), field.name());
            }
            CodeBlock.Builder body = CodeBlock.builder();
            for (FieldSource field : groupFields) {
                //通过constructIndex获取field，其实我觉得放group里面合适，但反正能用
                body.addStatement("$N($N, $N)",
                        fieldResult[field.constructIndex()].arraySetter(),
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
                .addStatement("this.$N = size",sizeField);
        for (GroupResult gSpec : groupResults) {
            constructor.addStatement("this.$N = new $T[size * $N]",
                    gSpec.arrayField(), gSpec.elementType(), gSpec.sizeConstName());
        }
        classBuilder.addMethod(constructor.build());


        //Element

        ElementResult elementResult = ElementResult.gen(store,fieldResult,fields);

        classBuilder.addMethod(elementResult.setElement());
        classBuilder.addMethod(elementResult.getElement());
        if(elementResult.getElementToDest()!=null)
            classBuilder.addMethod(elementResult.getElementToDest());

        // 实现 AbstractList

        //懒得写一个内部类了，甚至不如这个直观
        MethodSpec toList=MethodSpec.methodBuilder("toList")
                .addJavadoc(
                        "把数组存储当成一个定长列表来用。\n"+
                        "@returns 定长列表，推荐作为只调用 set 的消费者使用。"
                )
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(
                        ClassName.get(List.class),
                        store.elementName()
                ))
                .addCode(
"""
return new $1T<$2T>(){
    @$4T
    public $2T get(int i){
        return $3T.this.get(i);
    }
    
    @$4T
    public $2T set(int i, $2T element){
        $2T p = get(i);
        $3T.this.set(i, element);
        return p;
    }
    
    @$4T
    public int size(){
        return $3T.this.$5N;
    }
};
""",
                        AbstractList.class,
                        store.elementName(),
                        store.storeName(),
                        Override.class,
                        sizeField
                )
                .build();

        classBuilder.addMethod(toList);

        /*
        classBuilder.addMethod(
                MethodSpec.methodBuilder("size")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.INT)
                        .addStatement("return $N", sizeField)
                        .build()
        );
        */

        JavaFile javaFile = JavaFile.builder(store.storeName().packageName(), classBuilder.build())
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
                    g -> new GroupSource(g, field.dataType(), new ArrayList<>(), hasSameNameInField(g, fieldInfos)));
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

    public boolean hasSameNameInField(String groupName, List<FieldSource> fieldSources){
        for(FieldSource f: fieldSources){
            if(groupName.equals(f.name()))
                return true;
        }
        return false;
    }

    private Optional<List<FieldSource>> getFieldFromRecord(List<? extends Element> components) {
        List<FieldSource> fields = new ArrayList<>();
        int i = 0;
        boolean hasError = false;
        for (Element comp : components) {
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
                fields.add(new FieldSource(name, "$N."+name+"()", dataType, dataLength, fieldType, group, i, fieldSource));
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

    private Optional<List<FieldSource>> getFieldFromClass(List<? extends Element> components) {
        List<FieldSource> fields = new ArrayList<>();
        int i = 0;
        boolean hasError = false;
        for (Element comp : components) {
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
                fields.add(new FieldSource(name, "$N."+name, dataType, dataLength, fieldType, group, i, fieldSource));
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
                } else if ("dataType".equals(key)) {
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