package test.record.joml;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import yee.pltision.magicstorage.annotation.GenStore;
import yee.pltision.magicstorage.annotation.Joml;

@GenStore
public record Quad (
     @Joml Matrix4f transform,
     @Joml Vector4f texCrood,
     @Joml Vector4f rgba
){}