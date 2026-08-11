/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package yee.pltision.magicstorage.processor.step.src;

import com.palantir.javapoet.TypeName;

import java.util.List;

public record GroupSource(String name, TypeName dataType, List<FieldSource> fields) {
}