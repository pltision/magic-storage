package yee.pltision.soa.processor.step.src;

import com.palantir.javapoet.TypeName;
import org.jetbrains.annotations.Nullable;

public record FieldSource(String name, TypeName dataType, int dataLength, TypeName filedType, String group, int constructIndex, @Nullable CompoundFieldSource code) {
}
