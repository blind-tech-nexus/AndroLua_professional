package android.support.annotation;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;
@Retention(CLASS)
@Target({FIELD,METHOD})
public @interface GuardedBy {
    String value();
}
