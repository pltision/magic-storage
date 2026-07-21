package yee.pltision.soa.processor;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;

public record FieldCodeBlock(TypeName type, String[] args,
                             // array offset field
                             CodeBlock getAsArray,
                             // array offset
                             CodeBlock constructFromArray,
                             // array offset dist
                             CodeBlock setFromArray
) {
}
