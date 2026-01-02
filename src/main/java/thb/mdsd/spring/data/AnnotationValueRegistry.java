package thb.mdsd.spring.data;

import lombok.Getter;
import lombok.NonNull;

public enum AnnotationValueRegistry {

    REQUEST_MAPPING_PATH("value"),
    REQUEST_MAPPING_METHOD("method"),
    REQUEST_MAPPING_CONSUMES("consumes"),
    REQUEST_MAPPING_PRODUCES("produces"),
    API_OPERATION_VALUE("value");

    @Getter
    final String value;

    AnnotationValueRegistry(@NonNull String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}