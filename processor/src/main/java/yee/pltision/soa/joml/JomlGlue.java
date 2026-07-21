package yee.pltision.soa.joml;

import com.palantir.javapoet.*;
import org.joml.*;
import yee.pltision.soa.processor.FieldCodeBlock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// Hey guys, I think I found a glue!
public class JomlGlue {
    public static final Map<TypeName, FieldCodeBlock> ELEMENT_MAP = create();

    public static Map<TypeName, FieldCodeBlock> create() {
        List<Class<?>> jomlClasses = Stream.of(
                AxisAngle4d.class,
                AxisAngle4f.class,
                ConfigurationException.class,
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
                //只保留数值类型，很合理
                .filter(clazz -> {
                    String name=clazz.getSimpleName();
                    return name.endsWith("f")||name.endsWith("d")||name.endsWith("i")||name.endsWith("L");
                }).toList();

        jomlClasses.forEach(clazz -> {
            System.out.println(clazz.getSimpleName());
        });

        Map<TypeName, FieldCodeBlock> elementMap = new java.util.HashMap<>();

        jomlClasses.forEach(clazz -> elementMap.put(TypeName.get(clazz), createElementSpecs(clazz)));

        return elementMap;


    }

    public static FieldCodeBlock createElementSpecs(Class<?> clazz) {
        Class<?> dataType = getTypeFromName(clazz.getSimpleName());
/*
        MethodSpec getArray = MethodSpec.methodBuilder("getArray")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(ClassName.get(clazz), "element")
                .addParameter(ArrayTypeName.of(TypeName.get(dataType).unbox()), "array")
                .addParameter(TypeName.INT, "offset")
                .addStatement("element.get($N, $N)", "array", "offset")
                .build();

        MethodSpec setArray = MethodSpec.methodBuilder("setArray")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(ClassName.get(clazz), "element")
                .addParameter(ArrayTypeName.of(TypeName.get(dataType).unbox()), "array")
                .addParameter(TypeName.INT, "offset")
                .addStatement("element.set($N, $N)", "array", "offset")
                .build();

        MethodSpec arrayConstruct = MethodSpec.methodBuilder("arrayConstruct")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(ArrayTypeName.of(TypeName.get(dataType).unbox()), "array")
                .addParameter(TypeName.INT, "offset")
                .addStatement("return new $T($N, $N)", clazz, "array", "offset")
                .build();
*/
        String[] args = getArgs(clazz);

//        int size= args.length;

//        return new ElementSpecs(TypeName.get(dataType), size, getArray, arrayConstruct, setArray);

        CodeBlock getAsArray = CodeBlock.builder()
                .addStatement("$N.get($N, $N)", "field", "array", "offset")
                .build();

        CodeBlock constructFromArray = CodeBlock.builder()
                .addStatement("return new $T($N, $N)", clazz, "array", "offset")
                .build();

        CodeBlock setFromArray = CodeBlock.builder()
                .addStatement("$N.set($N, $N)", "dist", "array", "offset")
                .build();


        return new FieldCodeBlock(TypeName.get(dataType).unbox(), args,
                getAsArray, constructFromArray, setFromArray
        );
    }

    public static Class<? extends Number> getTypeFromName(String name){
        if(name.endsWith("f")){
            return Float.class;
        }else if(name.endsWith("d")){
            return Double.class;
        }else if(name.endsWith("i")){
            return Integer.class;
        }else if(name.endsWith("L")){
            return Long.class;
        }else{
            throw new IllegalArgumentException("Unknown type: "+name);
        }

    }

    public static String[] getArgs(Class<?> clazz) {
        List<String> args=new ArrayList<>(16);
        for (Field field: clazz.getFields()){
            if (!java.lang.reflect.Modifier.isStatic(field.getType().getModifiers())) {
                args.add(field.getName());
            }
        }

        return args.toArray(new String[0]);
    }

    public static int sizeFromName(String name) {
        return switch (name) {
            case "Vector2f", "Vector2d", "Vector2i", "Vector2L" -> 2;
            case "Vector3f", "Vector3d", "Vector3i", "Vector3L" -> 3;
            case "Vector4f", "Vector4d", "Vector4i", "Vector4L" -> 4;
            case "Matrix2f", "Matrix2d" -> 4;
            case "Matrix3f", "Matrix3d" -> 9;
            case "Matrix4f", "Matrix4d" -> 16;
            case "Quaternionf", "Quaterniond" -> 4;
            case "AxisAngle4f", "AxisAngle4d" -> 4;
            default -> throw new IllegalArgumentException("Unknown class: " + name);
        };
    }
}