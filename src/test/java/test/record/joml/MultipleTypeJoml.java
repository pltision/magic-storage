package test.record.joml;

import org.joml.Vector2f;
import org.joml.Vector2i;
import yee.pltision.soa.annotation.Field;
import yee.pltision.soa.annotation.Joml;
import yee.pltision.soa.annotation.SoA;

@SoA
public record MultipleTypeJoml(
        @Field(group = "f") @Joml Vector2f one,
        @Field(group = "i") @Joml Vector2i tow
) { }