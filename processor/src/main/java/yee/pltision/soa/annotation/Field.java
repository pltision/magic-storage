package yee.pltision.soa.annotation;

import java.lang.annotation.*;

@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.SOURCE)
public @interface Field {
    String group();

    // 我不想做
//    String groupArrayName() default "";
//    String groupSetMethodName() default "";
//    String groupGetMethodName() default "";
}
