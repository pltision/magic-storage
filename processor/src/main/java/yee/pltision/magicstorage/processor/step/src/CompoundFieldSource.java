package yee.pltision.magicstorage.processor.step.src;

import com.palantir.javapoet.TypeName;

public record CompoundFieldSource(TypeName dataType, TypeName fliedType, String[] args,
                                  // array offset field
                                  String getField,
                                  // array offset
                                  String getFieldToDest,
                                  // array offset dist
                                  String setField
) {
}
