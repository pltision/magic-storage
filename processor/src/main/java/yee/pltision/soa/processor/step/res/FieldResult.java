package yee.pltision.soa.processor.step.res;

import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import org.jetbrains.annotations.Nullable;
import yee.pltision.soa.processor.step.src.FieldInfo;

public record FieldResult(FieldInfo info, FieldSpec offsetConst, FieldSpec sizeConst,
                   MethodSpec arrayGetter, @Nullable MethodSpec arrayGetWithDist,
                   MethodSpec arraySetter, @Nullable MethodSpec arraySetPrimitive
) {
}