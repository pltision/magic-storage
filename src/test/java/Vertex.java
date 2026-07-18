import yee.pltision.soa.annotation.Field;
import yee.pltision.soa.annotation.SoA;

@SoA
public record Vertex(
    @Field(group = "position") int x,
    @Field(group = "position") int y,
    @Field(group = "color") int r,
    @Field(group = "color") int g,
    @Field(group = "color") int b
) { }