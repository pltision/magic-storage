package test.joml;

import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector4f;
import yee.pltision.soa.annotation.Field;
import yee.pltision.soa.annotation.SoA;
import yee.pltision.soa.annotation.StructElementGlue;
import yee.pltision.soa.joml.JomlGlueGenerator;

@SoA
public record ComplexJomlVertex(
        @Field(group = "vertex") @StructElementGlue(glue = JomlGlueGenerator.class, mapFieldName = "ELEMENT_MAP") Vector3f pos,
        @Field(group = "vertex") @StructElementGlue(glue = JomlGlueGenerator.class, mapFieldName = "ELEMENT_MAP") Vector4f color,
        @Field(group = "chunk") @StructElementGlue(glue = JomlGlueGenerator.class, mapFieldName = "ELEMENT_MAP") Vector2i chunkPos
) { }