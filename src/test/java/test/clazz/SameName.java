package test.clazz;

import yee.pltision.magicstorage.annotation.Field;
import yee.pltision.magicstorage.annotation.GenStore;

@GenStore
public class SameName {
    @Field(group = "f") public float f;
}