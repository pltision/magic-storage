package test.joml;

import org.joml.Vector3f;
import org.joml.Vector4f;
import yee.pltision.soa.annotation.StructElementGlue;
import yee.pltision.soa.annotation.SoA;
import yee.pltision.soa.joml.JomlGlue;

@SoA
public record JomlVertex(
        @StructElementGlue(glue = JomlGlue.class, mapFieldName = "ELEMENT_MAP") Vector3f pos,
        @StructElementGlue(glue = JomlGlue.class, mapFieldName = "ELEMENT_MAP") Vector4f color
) { }