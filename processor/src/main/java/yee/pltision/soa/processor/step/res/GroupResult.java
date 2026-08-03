package yee.pltision.soa.processor.step.res;

import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.TypeName;

public record GroupResult(String name, TypeName elementType, int fieldCount,
                   String sizeConstName, String arrayFieldName,
                   FieldSpec sizeField, FieldSpec arrayField) {
    }