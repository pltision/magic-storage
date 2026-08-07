package yee.pltision.magicstorage.processor.step.src;

import com.palantir.javapoet.TypeName;

import java.util.List;

public record GroupSource(String name, TypeName dataType, List<FieldSource> fields) {
}