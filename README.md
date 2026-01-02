# Modellgetriebene Softwareentwicklung

Ein Projekt der Technischen Hochschule Brandenburg.

## Demonstrations Projekt

[spring-boot-rest-example](https://github.com/khoubyari/spring-boot-rest-example)

## Dependencies
- Java 25
- Lombok
- JavaParser (String zu Java parsing und AST Generator)
- Yaml (Parsing für .yml-Files)
- OpenJFX (UI/UX Library)

## Projekt ausführen

Aus dem Projekt ausführen:
```bash
mvn clean javafx:run
```

Compilieren und anschließend ausführen:
```bash
mvn clean install && java -jar /target/mdsd-1.0-SNAPSHOT.jar
```

## Features

### UI

- Auswahl und Anzeigen von Projekten
- Ausführen der Generation mit Statusmeldungen
- Auswahl eines Output-Paths

### Generation

Der Algorithmus sucht geziehlt nach Enums, Returns und Kommentaren und erzeugt daraufhin die OpenAPI-Dokumentation für ein Projekt.\
Dieser Ablauf kann beliebig verändert und angepasst werden und unterstützt aktuell die folgenden Bibliotheken für die Generation: "springframework", "jakarta" und "swagger"