package yee.pltision.soa.annotation;

import yee.pltision.soa.joml.JomlGlue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.SOURCE)
@StructElementGlue(glue = JomlGlue.class)
public @interface Joml {
}
