package test.record.primitive;

import yee.pltision.soa.annotation.Field;
import yee.pltision.soa.annotation.SoA;

@SoA
public record MultipleType(
        @Field(group = "f") float a,
        @Field(group = "f") float b,
        @Field(group = "f") float c,
        @Field(group = "i") int d,
        @Field(group = "i") int e/*,
        @Field(group = "i") int f*/
        // 不可与 group 重名
) { }