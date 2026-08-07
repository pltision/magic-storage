package yee.pltision.magicstorage.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface Field {
    String group();

    // 我不想做
//    String groupArrayName() default "";
//    String groupSetMethodName() default "";
//    String groupGetMethodName() default "";
}
