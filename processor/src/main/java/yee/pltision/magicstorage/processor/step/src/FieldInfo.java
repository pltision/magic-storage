package yee.pltision.magicstorage.processor.step.src;

import com.palantir.javapoet.TypeName;
import org.jetbrains.annotations.Nullable;

public record FieldInfo(String name, TypeName dataType, int dataLength, TypeName filedType, String group, int constructIndex, @Nullable CompoundFieldSource code) {
}
