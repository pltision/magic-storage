package yee.pltision.soa.joml;

import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.CodeBlock;
import org.joml.*;
import yee.pltision.soa.processor.FieldCodeBlock;
import yee.pltision.soa.processor.spi.ElementGlueProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// Hey guys, I think I found a glue!
public class JomlGlue implements ElementGlueProvider {

    public static final Map<TypeName, FieldCodeBlock> ELEMENT_MAP = create();

    @Override
    public Map<TypeName, FieldCodeBlock> getElementMap() {
        return ELEMENT_MAP;
    }

    public static Map<TypeName, FieldCodeBlock> create() {
        List<Class<?>> jomlClasses = Stream.of(
                AxisAngle4d.class,
                AxisAngle4f.class,
                // 注意：删除了 Double.class（之前错误添加）
                FrustumIntersection.class,
                FrustumRayBuilder.class,
                GeometryUtils.class,
                Interpolationd.class,
                Interpolationf.class,
                Intersectiond.class,
                Intersectionf.class,
                Matrix2d.class,
                Matrix2dc.class,
                Matrix2f.class,
                Matrix2fc.class,
                Matrix3d.class,
                Matrix3dStack.class,
                Matrix3dc.class,
                Matrix3f.class,
                Matrix3fStack.class,
                Matrix3fc.class,
                Matrix3x2d.class,
                Matrix3x2dStack.class,
                Matrix3x2dc.class,
                Matrix3x2f.class,
                Matrix3x2fStack.class,
                Matrix3x2fc.class,
                Matrix4d.class,
                Matrix4dStack.class,
                Matrix4dc.class,
                Matrix4f.class,
                Matrix4fStack.class,
                Matrix4fc.class,
                Matrix4x3d.class,
                Matrix4x3dStack.class,
                Matrix4x3dc.class,
                Matrix4x3f.class,
                Matrix4x3fStack.class,
                Matrix4x3fc.class,
                Options.class,
                PolygonsIntersection.class,
                Quaterniond.class,
                QuaterniondInterpolator.class,
                Quaterniondc.class,
                Quaternionf.class,
                QuaternionfInterpolator.class,
                Quaternionfc.class,
                Random.class,
                RayAabIntersection.class,
                RoundingMode.class,
                SimplexNoise.class,
                Vector2L.class,
                Vector2Lc.class,
                Vector2d.class,
                Vector2dc.class,
                Vector2f.class,
                Vector2fc.class,
                Vector2i.class,
                Vector2ic.class,
                Vector3L.class,
                Vector3Lc.class,
                Vector3d.class,
                Vector3dc.class,
                Vector3f.class,
                Vector3fc.class,
                Vector3i.class,
                Vector3ic.class,
                Vector4L.class,
                Vector4Lc.class,
                Vector4d.class,
                Vector4dc.class,
                Vector4f.class,
                Vector4fc.class,
                Vector4i.class,
                Vector4ic.class
        )
                // 只保留数值类型（以 f/d/i/L 结尾）
                .filter(clazz -> {
                    String name = clazz.getSimpleName();
                    return name.endsWith("f") || name.endsWith("d") || name.endsWith("i") || name.endsWith("L");
                })
                .toList();

        // （可选）打印调试信息
        jomlClasses.forEach(clazz -> System.out.println(clazz.getSimpleName()));

        Map<TypeName, FieldCodeBlock> elementMap = new java.util.HashMap<>();
        for (Class<?> clazz : jomlClasses) {
            elementMap.put(TypeName.get(clazz), createElementSpecs(clazz));
        }
        return elementMap;
    }

    private static FieldCodeBlock createElementSpecs(Class<?> clazz) {
        Class<?> dataType = getTypeFromName(clazz.getSimpleName());
        String[] args = getArgs(clazz);

        CodeBlock getAsArray = CodeBlock.builder()
                .addStatement("$N.get($N, $N)", "field", "array", "offset")
                .build();

        CodeBlock constructFromArray = CodeBlock.builder()
                .addStatement("return new $T($N, $N)", clazz, "array", "offset")
                .build();

        CodeBlock setFromArray = CodeBlock.builder()
                .addStatement("$N.set($N, $N)", "dist", "array", "offset")
                .build();

        return new FieldCodeBlock(
                TypeName.get(dataType).unbox(),
                args,
                getAsArray,
                constructFromArray,
                setFromArray
        );
    }

    private static Class<? extends Number> getTypeFromName(String name) {
        if (name.endsWith("f")) {
            return Float.class;
        } else if (name.endsWith("d")) {
            return Double.class;
        } else if (name.endsWith("i")) {
            return Integer.class;
        } else if (name.endsWith("L")) {
            return Long.class;
        } else {
            throw new IllegalArgumentException("Unknown type: " + name);
        }
    }

    private static String[] getArgs(Class<?> clazz) {
        List<String> args = new ArrayList<>(16);
        for (Field field : clazz.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                args.add(field.getName());
            }
        }
        return args.toArray(new String[0]);
    }
}