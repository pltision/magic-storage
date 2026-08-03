package yee.pltision.soa.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.SOURCE)
@Repeatable(Glue.Glues.class)
public @interface Glue {

    /**
     *
     * @return 胶水可以匹配的类型。一般我会把一堆胶水写进一个注解，让处理器自己选。
     */
    Class<?> targetType() default Object.class;

    /**
     * @return 构成组件的类型。如果能转换成原初类型则会自动转换，否则保留原类型。
     */
    Class<?> dataType();

    /**
     * @return 组件的所有字段，如果为空自动扫描 <code>public static</code> 字段
     */
    String[] args() default {};

    // 提示：占位符可以使用索引，例如 "$1N"、"$2N"、"$3T"
    // 填入的是方法体，可以有多个语句

    /**
     * <p>F getField(int index)</p>
     * <p>Argument: Type, array, (index * groupSize + offset)</p>
     */
    String getField() default "return new $T($N, $L);";

    /**
     * <p>F getField(int index, F dest)</p>
     * <p>Argument: dest, array, (index * groupSize + offset)</p>
     * @return 如果为<code>""</code>，则不生成此方法
     */
    String getFieldToDest() default "return $N.set($N, $L);";

    /**
     * <p>void setField(int index, F field)</p>
     * <p>Argument: field, array, (index * groupSize + offset)</p>
     */
    String setField() default "$N.get($N, $L);";

    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.CLASS)
    @interface Glues {
        Glue[] value();
    }

}