/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
