package yee.pltision.soa.processor;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;

public record FieldCodeBlock(TypeName dataType, TypeName fliedType, String[] args,
                             // array offset field
                             String getAsArray,
                             // array offset
                             String constructFromArray,
                             // array offset dist
                             String setFromArray
) {
}
