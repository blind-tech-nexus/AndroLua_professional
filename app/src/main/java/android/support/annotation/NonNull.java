package android.support.annotation;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;
@Retention(CLASS)
@Target({METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,TYPE_USE})
public @interface NonNull {}
