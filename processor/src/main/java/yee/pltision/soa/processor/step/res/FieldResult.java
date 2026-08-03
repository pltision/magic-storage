package yee.pltision.soa.processor.step.res;

import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import org.jetbrains.annotations.Nullable;
import yee.pltision.soa.processor.step.src.FieldSource;

public record FieldResult(FieldSource info, FieldSpec offsetConst, FieldSpec sizeConst,
                          MethodSpec arrayGetter, @Nullable MethodSpec arrayGetWithDist,
                          MethodSpec arraySetter, @Nullable MethodSpec arraySetPrimitive
) {
}