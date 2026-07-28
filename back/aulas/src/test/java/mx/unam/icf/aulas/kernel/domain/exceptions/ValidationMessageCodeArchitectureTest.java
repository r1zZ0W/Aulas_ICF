package mx.unam.icf.aulas.kernel.domain.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the string↔enum coupling introduced by using {@link ErrorCode} names as the literal
 * {@code message} of Jakarta Bean Validation annotations (see {@link ErrorCode}'s Javadoc).
 *
 * <p>That coupling is invisible to the compiler: {@code message = "USER_EMAIL_TAKEN"} is just a
 * string literal, so an IDE rename of {@code ErrorCode.USER_EMAIL_TAKEN} does not touch it. If
 * they drift apart, {@code GlobalExceptionHandler.isErrorCodeName} silently degrades the field
 * error to a generic {@code FIELD_INVALID_FORMAT}/{@code VALIDATION_FAILED} code — no exception,
 * no failing request, just a less specific message reaching the user. This test is the only
 * place that coupling is checked, so every {@code *RequestDTO} record's constraint messages
 * must resolve to a real {@link ErrorCode} constant.</p>
 */
class ValidationMessageCodeArchitectureTest {

    private static final Set<String> VALIDATION_ANNOTATION_PACKAGES =
            Set.of("jakarta.validation.constraints", "org.hibernate.validator.constraints");

    @Test
    void everyRequestDtoConstraintMessageIsAKnownErrorCode() throws Exception {
        List<String> offenders = new ArrayList<>();

        for (Class<?> dto : findRequestDtoClasses()) {
            for (RecordComponent component : dto.getRecordComponents()) {
                for (Annotation annotation : component.getAnnotations()) {
                    if (!isValidationAnnotation(annotation)) continue;

                    String message = readMessageAttribute(annotation);
                    if (message == null || !isKnownErrorCode(message)) {
                        offenders.add(dto.getSimpleName() + "#" + component.getName()
                                + " @" + annotation.annotationType().getSimpleName()
                                + " message=\"" + message + "\"");
                    }
                }
            }
        }

        assertThat(offenders)
                .as("Every @NotBlank/@Size/@Pattern/... message on a *RequestDTO must be the literal "
                        + "name of an ErrorCode constant (see ErrorCode's Javadoc) so "
                        + "GlobalExceptionHandler can resolve it into a field error the frontend "
                        + "catalog understands. Offending fields")
                .isEmpty();
    }

    private static boolean isValidationAnnotation(Annotation annotation) {
        String pkg = annotation.annotationType().getPackageName();
        return VALIDATION_ANNOTATION_PACKAGES.contains(pkg);
    }

    private static String readMessageAttribute(Annotation annotation) throws Exception {
        Method messageMethod = annotation.annotationType().getMethod("message");
        Object value = messageMethod.invoke(annotation);
        return value instanceof String s ? s : null;
    }

    private static boolean isKnownErrorCode(String message) {
        try {
            ErrorCode.valueOf(message);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Every {@code record} whose simple name ends in {@code RequestDTO}, anywhere under the app's base package. */
    private static List<Class<?>> findRequestDtoClasses() throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:mx/unam/icf/aulas/**/*RequestDTO.class");

        List<Class<?>> classes = new ArrayList<>();
        for (Resource resource : resources) {
            String path = resource.getURL().toString();
            String className = path.substring(path.indexOf("mx/unam/icf/aulas"), path.length() - ".class".length())
                    .replace('/', '.');
            Class<?> clazz = Class.forName(className);
            if (clazz.isRecord()) classes.add(clazz);
        }

        assertThat(classes)
                .as("Sanity check: the classpath scan for *RequestDTO records found nothing — "
                        + "the resource pattern is probably wrong, not that the DTOs disappeared")
                .isNotEmpty();

        return classes;
    }
}
