package yee.pltision.magicstorage.processor.step.res;

import cn.hutool.core.text.NamingCase;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import org.jetbrains.annotations.Nullable;
import yee.pltision.magicstorage.processor.step.src.FieldSource;

import javax.lang.model.element.Modifier;

import static yee.pltision.magicstorage.processor.StoreProcessor.indexName;

public record FieldResult(String name, FieldSpec offsetConst, FieldSpec sizeConst,
                          MethodSpec arrayGetter, @Nullable MethodSpec arrayGetWithDist,
                          MethodSpec arraySetter, @Nullable MethodSpec arraySetPrimitive
) {

    public static FieldResult gen(GroupResult group, FieldSource field, int offset){
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
                            group.arrayField(), indexName, group.sizeConstName(), offsetConstName)
                    .build();

            // float setField(int index, float f)
            MethodSpec setter = MethodSpec.methodBuilder("set" + capFileName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, indexName)
                    .addParameter(fieldType, fieldName)
                    .addStatement("$N[$N * $N + $N] = $N",
                            group.arrayField(), indexName, group.sizeConstName(), offsetConstName, fieldName)
                    .build();

            return new FieldResult(field.name(), offsetField, sizeConst,
                    getter, null,
                    setter, null
            );
        }
        else {

            // F getField(int index)
            MethodSpec getter = MethodSpec.methodBuilder("get" + capFileName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, indexName)
                    .returns(fieldType)
                    .addCode(field.code().getField(), field.filedType(), group.arrayField(),
                            CodeBlock.of("($N * $N + $N)", indexName, group.sizeField(), offsetConstName).toString())
                    .build();

            String destName="dest";
            // F getField(int index, F dest)
            MethodSpec getWithDist = MethodSpec.methodBuilder("get" + capFileName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, indexName)
                    .addParameter(fieldType, destName)
                    .returns(fieldType)
                    .addCode(field.code().getFieldToDest(), destName, group.arrayField(),
                            CodeBlock.of("($N * $N + $N)", indexName, group.sizeField(), offsetConstName).toString())
                    .build();

            // F setField(int index, F field)
            MethodSpec setter = MethodSpec.methodBuilder("set" + capFileName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, indexName)
                    .addParameter(fieldType, fieldName)
                    .addCode(field.code().setField(),
                            fieldName,
                            group.arrayField(),
                            CodeBlock.of("($N * $N + $N)", indexName, group.sizeField(), offsetConstName).toString())
                    .build();

            // F setField(int index, float... data)
            MethodSpec.Builder setByPrimitive = MethodSpec.methodBuilder("set"+capFileName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, indexName);

            String[] args = field.code().args();
            for(int i=0;i<args.length;i++){
                setByPrimitive.addParameter(field.dataType(), args[i]);
                setByPrimitive.addStatement("$N[$N * $N + $N + $L] = $N",
                        group.arrayField(),
                        group.sizeField(), indexName, offsetField, i,
                        args[i]
                );
            }

            return new FieldResult(field.name(), offsetField, sizeConst,
                    getter,getWithDist,
                    setter, setByPrimitive.build()
            );
        }
    }
}