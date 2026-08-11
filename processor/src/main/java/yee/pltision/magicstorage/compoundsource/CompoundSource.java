/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package yee.pltision.magicstorage.compoundsource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface CompoundSource {

    @Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
    @interface Argument{}

    /**
     * param: F[] array, int offset
     * return: S
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.SOURCE)
    @interface ConstructFromArray{}

    /**
     * 这是一个可选项，record等不可变类可以不实现此方法
     * param: S dest, F[] array, int offset
     * return: S
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.SOURCE)
    @interface GetDestFromArray{}

    /**
     * param: S source, int offset
     * return: void
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.SOURCE)
    @interface SetToArray{}

}