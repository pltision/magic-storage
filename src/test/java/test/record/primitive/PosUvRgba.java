package test.record.primitive;

import yee.pltision.magicstorage.annotation.Field;
import yee.pltision.magicstorage.annotation.GenStore;

@GenStore
public record PosUvRgba(
        @Field(group = "pos") float x,
        @Field(group = "pos") float y,
        @Field(group = "pos") float z,
        @Field(group = "uv") float u,
        @Field(group = "uv") float v,
        @Field(group = "rgba") float r,
        @Field(group = "rgba") float g,
        @Field(group = "rgba") float b,
        @Field(group = "rgba") float a
){ }