package yee.pltision.soa.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface StructElementGlue{

    Class<?> glue();

    String mapFieldName() default "ELEMENT_MAP";

}
