package thb.mdsd.spring.data;

import lombok.Getter;
import lombok.NonNull;

@Getter
public enum CommonAnnotationNameRegistry {

    SPRING_BOOT_APPLICATION("SpringBootApplication"),
    ENTITY("Entity"),
    CONTROLLER("Controller"),
    REST_CONTROLLER("RestController"),
    REQUEST_MAPPING("RequestMapping"),
    RESPONSE_STATUS("ResponseStatus"),
    ENABLE_SWAGGER_2("EnableSwagger2"),
    API_OPERATION("ApiOperation"),
    REQUEST_BODY("RequestBody");

    final String name;

    CommonAnnotationNameRegistry(@NonNull String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
