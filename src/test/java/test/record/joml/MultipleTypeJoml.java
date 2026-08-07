package test.record.joml;

import org.joml.Vector2f;
import org.joml.Vector2i;
import yee.pltision.magicstorage.annotation.Field;
import yee.pltision.magicstorage.annotation.Joml;
import yee.pltision.magicstorage.annotation.GenStore;

@GenStore
public record MultipleTypeJoml(
        @Field(group = "f") @Joml Vector2f one,
        @Field(group = "i") @Joml Vector2i tow
) { }