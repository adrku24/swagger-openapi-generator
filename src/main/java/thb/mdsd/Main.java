package thb.mdsd;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.NonNull;
import thb.mdsd.plantuml.PlantUMLGenerator;
import thb.mdsd.spring.SpringBootExtractor;
import thb.mdsd.swagger.SwaggerAPIExport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    private static boolean isHidden(@NonNull Path path) {
        try {
            return Files.isHidden(path);
        } catch (IOException _) {
            return false;
        }
    }

    private static boolean isDirectory(@NonNull Path path) {
        try {
            return Files.isDirectory(path);
        } catch (Exception _) {
            return false;
        }
    }

    private final Label projectHeaderLabel = new Label("Spring Visualizer - Swagger OpenAPI Generator");
    private final Label statusLabel = new Label("Wähle ein Projektordner");
    private final TreeView<String> fileTreeView = new TreeView<>();
    private final Button selectFolderButton = new Button("Auswählen");
    private final Button generateButton = new Button("Generiere OpenAPI");
    private final Button generatePlantUmlButton = new Button("Generiere PlantUML");
    private Stage primaryStage;
    private String selectedPath;

    @Override
    public void start(@NonNull Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("SpringBoot Swagger Generator");

        projectHeaderLabel.setStyle("-fx-font-weight: bold; -fx-padding: 4;");
        projectHeaderLabel.setAlignment(Pos.CENTER);
        projectHeaderLabel.setMaxWidth(Double.MAX_VALUE);

        statusLabel.setStyle("-fx-font-weight: bold; -fx-padding: 2;");
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        selectFolderButton.setOnAction(_ -> openDirectoryChooser());
        generateButton.setOnAction(this::handleGenerateAction);
        generatePlantUmlButton.setOnAction(this::handleGeneratePlantUml);

        final TreeItem<String> rootItem = new TreeItem<>("Kein Ordner ausgewählt...");
        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(true);

        final HBox buttonContainer = new HBox(10);
        buttonContainer.getChildren().addAll(selectFolderButton, generateButton, generatePlantUmlButton);
        buttonContainer.setAlignment(Pos.CENTER);

        final BorderPane information = new BorderPane();
        information.setTop(projectHeaderLabel);
        information.setCenter(statusLabel);

        final BorderPane root = new BorderPane();
        root.setTop(information);
        root.setCenter(fileTreeView);
        root.setBottom(buttonContainer);

        selectFolderButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setMaxWidth(Double.MAX_VALUE);

        BorderPane.setMargin(buttonContainer, new Insets(10));
        BorderPane.setMargin(statusLabel, new Insets(10));

        final Scene scene = new Scene(root, 600, 450);
        stage.setScene(scene);
        stage.show();
    }

    private void handleGeneratePlantUml(@NonNull ActionEvent event) {
        if (selectedPath == null) {
            statusLabel.setText("Kein Projektpfad ausgewählt!");
            return;
        }

        final FileChooser chooser = new FileChooser();
        chooser.setTitle("PlantUML exportieren");
        chooser.setInitialDirectory(new File(selectedPath));
        chooser.setInitialFileName("diagram.puml");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PlantUML", "*.puml"));

        final File out = chooser.showSaveDialog(primaryStage);
        if (out == null) {
            statusLabel.setText("Abgebrochen");
            return;
        }

        try {
            PlantUMLGenerator.generateFromProject(Path.of(selectedPath), out.toPath());
            statusLabel.setText("PlantUML generiert: " + out.getAbsolutePath());
        } catch (Exception e) {
            statusLabel.setText("Fehler: " + e.getMessage());
        }
    }

    private void openDirectoryChooser() {
        final DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Erstelle eine Swagger Dokumentation aus einem SpringBoot Projekt.");
        final File selectedDirectory = directoryChooser.showDialog(primaryStage);

        if (selectedDirectory != null) {
            statusLabel.setText("Projekt ausgewählt: " + selectedDirectory.getAbsolutePath());
            final TreeItem<String> rootItem = new TreeItem<>(selectedDirectory.getName() + " (Root)");
            rootItem.setExpanded(true);
            buildDirectoryTree(rootItem, selectedDirectory.toPath());
            fileTreeView.setRoot(rootItem);
            selectedPath = selectedDirectory.getAbsolutePath();
        } else {
            statusLabel.setText("Bitte wähle ein SpringBoot-Projektordner aus.");
            fileTreeView.setRoot(new TreeItem<>("Bitte wähle ein SpringBoot-Projektordner aus."));
            selectedPath = null;
        }
    }

    private void buildDirectoryTree(@NonNull TreeItem<String> parent, @NonNull Path dirPath) {
        try (final Stream<Path> stream = Files.list(dirPath)) {
            stream.filter(p -> !isHidden(p))
                .sorted(Comparator.comparing(Main::isDirectory, Comparator.reverseOrder()).thenComparing(Path::getFileName))
                .forEach(path -> {
                    final String name = path.getFileName().toString();
                    final TreeItem<String> item = new TreeItem<>(name);
                    parent.getChildren().add(item);

                    if (isDirectory(path)) {
                        item.getChildren().add(new TreeItem<>("Lädt..."));
                        item.expandedProperty().addListener((observable, oldValue, newValue) -> {
                            if (newValue && item.getChildren().size() == 1 && item.getChildren().getFirst().getValue().equals("Lädt...")) {
                                item.getChildren().clear();
                                buildDirectoryTree(item, path);
                            }
                        });
                    }
                });
        } catch (Exception exception) {
            parent.getChildren().add(new TreeItem<>("Ordner konnte nicht geladen werden: " + exception.getMessage()));
        }
    }

    private void handleGenerateAction(@NonNull ActionEvent event) {
        statusLabel.setText("Generiere...");

        if(selectedPath == null) {
            statusLabel.setText("Kein Projektpfad ausgewählt!");
            return;
        }

        final FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exportiere Swagger Datei");

        final File initialDir = new File(selectedPath);
        fileChooser.setInitialDirectory(initialDir);
        fileChooser.setInitialFileName("swagger-api.yml");

        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Yaml Swagger Datei", "*.yaml"));

        final SpringBootExtractor extractor = new SpringBootExtractor(selectedPath);

        final File outputFile = fileChooser.showSaveDialog(primaryStage);
        if (outputFile != null) {
            statusLabel.setText("Swagger OpenAPI Datei exportiert: " + outputFile.getAbsolutePath());

            try {
                new SwaggerAPIExport(extractor).export(outputFile);
            } catch (IOException exception) {
                statusLabel.setText("Aktion fehlgeschlagen: " + exception.getMessage());
            }
        } else {
            statusLabel.setText("Abgebrochen");
        }
    }
}