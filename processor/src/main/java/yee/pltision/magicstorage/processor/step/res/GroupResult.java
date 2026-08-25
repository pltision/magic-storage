/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package yee.pltision.magicstorage.processor.step.res;

import cn.hutool.core.text.NamingCase;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.TypeName;
import yee.pltision.magicstorage.processor.step.src.FieldSource;
import yee.pltision.magicstorage.processor.step.src.GroupSource;

import javax.lang.model.element.Modifier;

public record GroupResult(String name, TypeName elementType, int fieldCount,
                          String sizeConstName, String arrayFieldName,
                          FieldSpec sizeField, FieldSpec arrayField) {

    public static GroupResult gen(GroupSource group) {
        String groupName = group.name();

        //直接数，field和group其实相互依赖，但group可以暂时全用标量
        int sizeCount = 0;
        for (FieldSource field : group.fields()) {
            sizeCount += field.dataLength();
        }
        TypeName elementType = group.dataType();
        ArrayTypeName arrayType = ArrayTypeName.of(elementType);

        String sizeConstName = "SIZE_GROUP_" + NamingCase.toUnderlineCase(groupName).toUpperCase();
        FieldSpec sizeConst = FieldSpec.builder(int.class, sizeConstName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", sizeCount)
                .build();

        String arrayFieldName = groupName + "Array";
        FieldSpec arrayField = FieldSpec.builder(arrayType, arrayFieldName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .build();

        return new GroupResult(groupName, elementType, sizeCount, sizeConstName, arrayFieldName, sizeConst, arrayField);
    }
}