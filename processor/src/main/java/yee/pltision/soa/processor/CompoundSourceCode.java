package yee.pltision.soa.processor;

import com.palantir.javapoet.TypeName;

public record CompoundSourceCode(TypeName dataType, TypeName fliedType, String[] args,
                                 // array offset field
                                 String getField,
                                 // array offset
                                 String getFieldToDest,
                                 // array offset dist
                                 String setField
) {
}
