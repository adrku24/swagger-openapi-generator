package thb.mdsd.spring.data;

import lombok.Getter;
import lombok.NonNull;

@Getter
public enum AnnotationRegistry {

    // SpringBoot
    SPRING_BOOT_APPLICATION("org.springframework.boot.autoconfigure.SpringBootApplication"),
    SPRING_BEAN("org.springframework.context.annotation.Bean"),
    SPRING_REQUEST_BODY("org.springframework.web.bind.annotation.ResponseBody"),
    SPRING_RESPONSE_BODY("org.springframework.web.bind.annotation.ResponseBody"),
    SPRING_CONFIGURATION("org.springframework.context.annotation.Configuration"),
    SPRING_WEB_REST_CONTROLLER("org.springframework.web.bind.annotation.RestController"),
    SPRING_WEB_CONTROLLER("org.springframework.web.bind.annotation.Controller"),
    SPRING_AUTOCONFIGURE_ANY("org.springframework.boot.autoconfigure.*"),
    SPRING_WEB_ANY("org.springframework.web.bind.annotation.*"),

    // Jakarta
    JAKARTA_PERSISTENCE_ENTITY("jakarta.persistence.Entity"),
    JAKARTA_PERSISTENCE_ANY("jakarta.persistence.*"),

    // Swagger
    SWAGGER_API("io.swagger.annotations.Api"),
    SWAGGER_API_OPERATION("io.swagger.annotations.ApiOperation"),
    SWAGGER_API_PARAM("io.swagger.annotations.ApiParam"),
    SWAGGER_ENABLE("springfox.documentation.swagger2.annotations.EnableSwagger2");

    final String packageName;

    AnnotationRegistry(@NonNull String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String toString() {
        return packageName;
    }
}
