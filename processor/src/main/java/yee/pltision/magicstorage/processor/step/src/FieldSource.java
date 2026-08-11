/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package yee.pltision.magicstorage.processor.step.src;

import com.palantir.javapoet.TypeName;
import org.jetbrains.annotations.Nullable;

public record FieldSource(
        String name,
        // $N.name()
        String getFromElement,
        TypeName dataType,
        int dataLength,
        TypeName filedType,
        String group,
        int constructIndex,
        @Nullable CompoundFieldSource code
) {
}
