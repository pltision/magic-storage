/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package yee.pltision.magicstorage.processor.step.src;

import com.palantir.javapoet.ClassName;
import org.jetbrains.annotations.Nullable;
import yee.pltision.magicstorage.StringUtil;

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

        String name = StringUtil.firstLower(storeName.simpleName());

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