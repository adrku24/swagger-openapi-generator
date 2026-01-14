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

## Documentation Combiner (PlantUML + OpenAPI)

Der Documentation Combiner ist ein ergänzendes Werkzeug zur Zusammenführung der automatisch generierten Dokumentationsartefakte dieses Projekts.
Er kombiniert PlantUML-Klassendiagramme und OpenAPI/Swagger-Spezifikationen zu einer gemeinsamen, integrierten Dokumentation in Form eines Markdown-Dokuments.

Der Combiner führt selbst keine Analyse des Quellcodes durch, sondern arbeitet ausschließlich auf den bereits generierten Ausgaben der Generatoren.

## Dependencies

- Python 3.9 oder höher
- Tkinter (standardmäßig in Python enthalten)
- (Optional) Java und PlantUML (für das Rendern von Diagrammen als PNG)

## Projekt ausführen

Aus dem Projektverzeichnis ausführen:
```bash
python doc_combiner.py
```

Anschließend können über die grafische Oberfläche:
- eine PlantUML-Datei (.puml)
- eine OpenAPI-Datei (.yml, .yaml oder .json)
- sowie ein Ausgabe-Pfad für die kombinierte Dokumentation
ausgewählt werden.

## Features
### UI
- Auswahl einer generierten PlantUML-Datei
- Auswahl einer generierten OpenAPI-Spezifikation
- Auswahl eines Ausgabe-Pfads für das kombinierte Dokument
- Statusmeldungen während der Generierung
- Optionales Rendern von PlantUML-Diagrammen als PNG

### Dokumentationszusammenführung
- Erzeugung eines gemeinsamen Markdown-Dokuments
- Einbettung der strukturellen Dokumentation (PlantUML)
- Einbettung der API-Dokumentation (OpenAPI/Swagger)
- Klare Trennung der einzelnen Dokumentationsbereiche
