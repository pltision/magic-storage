package yee.pltision.soa.processor.step.res;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import org.jetbrains.annotations.Nullable;
import yee.pltision.soa.StringUtil;
import yee.pltision.soa.processor.step.src.ElementSource;
import yee.pltision.soa.processor.step.src.FieldSource;

import javax.lang.model.element.Modifier;

import java.util.ArrayList;
import java.util.List;

import static yee.pltision.soa.processor.SoAProcessor.indexName;

public record ElementResult(
        MethodSpec setElement,
        MethodSpec getElement,
        @Nullable MethodSpec getElementToDest
) {
    public static ElementResult gen(ElementSource store, FieldResult[] fieldResult, List<FieldSource> fieldSource){

        // void setElement(int index, E element)

        String elementParam = StringUtil.firstLower(store.elementName().simpleName());
        MethodSpec.Builder setElement = MethodSpec.methodBuilder("set")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, indexName)
                .addParameter(store.elementName(), elementParam);

        for(int i=0;i<fieldResult.length;i++){
            setElement.addStatement("$N($N, $L)",
                    fieldResult[i].arraySetter(),
                    indexName,
                    CodeBlock.of(fieldSource.get(i).getFromElement(), elementParam)
            );
        }

        // Element getElement(int index)

        MethodSpec.Builder getElement = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(int.class, indexName)
                .returns(store.elementName());
        List<Object> getArgs = new ArrayList<>(fieldResult.length+1);
        getArgs.add(store.elementName());
        for (FieldResult result : fieldResult) {
            getArgs.add(CodeBlock.of("$N($N)", result.arrayGetter(), indexName));
        }
        getElement.addCode(store.constructor(), getArgs.toArray());

        return new ElementResult(setElement.build(), getElement.build(), null);
    }
}