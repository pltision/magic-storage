package yee.pltision.magicstorage.processor.step.src;

import cn.hutool.core.text.NamingCase;
import com.palantir.javapoet.ClassName;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

public record ElementSource(
        ClassName elementName,
        ClassName storeName,
        // return new T(F f...);
        String constructor,
        @Nullable String set
) {

    /*
        return new $T(f...);
     */
    public static String fullConstructor(List<FieldSource> fields) {
        StringBuilder builder=new StringBuilder();

        builder.append("return new $T(");
        for (
                Iterator<FieldSource> it = fields.iterator();
                it.hasNext();
        ){
            it.next();
            builder.append("$L");
            if(it.hasNext()){
                builder.append(", ");
            }
        }
        builder.append(");");

        return builder.toString();
    }

    /*
        $1T t  new $1T();
        t.f = $2N;
     */
    public static String emptyConstructor(List<FieldSource> fields, ClassName storeName) {
        StringBuilder builder=new StringBuilder();

        String name = NamingCase.toCamelCase(storeName.simpleName());

        builder.append("$1T ").append(name).append("= new $1T();\n");

        int i=2;
        for(FieldSource f: fields){
            builder.append(name).append(".").append(f.name()).append(" = $").append(i).append("L;\n");
            i++;
        }

        builder.append("return ").append(name).append(";");

        return builder.toString();
    }
}