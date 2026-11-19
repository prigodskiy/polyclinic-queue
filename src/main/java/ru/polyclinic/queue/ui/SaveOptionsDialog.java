package ru.polyclinic.queue.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SaveOptionsDialog extends Dialog<SaveOptionsDialog.SaveResult> {
    public record SaveResult(File file, int version) {}

    private static final String APP_FOLDER_NAME = "polyclinic-queue";

    private File selectedFile;
    private final TextField pathField = new TextField();
    private final ComboBox<Integer> versionBox = new ComboBox<>();
    private final Label warningLabel = new Label();

    public SaveOptionsDialog(Stage owner, String extension, String defaultFileName) {
        initOwner(owner);
        setTitle("Сохранение файла");
        setHeaderText("Настройки экспорта");

        ButtonType saveButtonType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(
                saveButtonType,
                new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE)
        );

        getDialogPane().setPrefWidth(460);

        Path defaultDir = resolveDefaultDirectory();
        selectedFile = defaultDir.resolve(defaultFileName).toFile();

        Label versionCaption = new Label("Версия формата:");

        versionBox.getItems().addAll(1, 2, 3);
        versionBox.getSelectionModel().select(Integer.valueOf(3));
        versionBox.setPrefWidth(100);

        HBox versionRow = new HBox(10, versionCaption, versionBox);
        versionRow.setAlignment(Pos.CENTER_LEFT);

        Label pathCaption = new Label("Сохранить как:");

        pathField.setEditable(false);
        pathField.setText(selectedFile.getAbsolutePath());
        pathField.positionCaret(pathField.getText().length());
        pathField.setStyle("-fx-cursor: hand;");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button chooseFileBtn = new Button("\uD83D\uDCC1");
        chooseFileBtn.setTooltip(new Tooltip("Выбрать другое место сохранения"));
        chooseFileBtn.setFocusTraversable(false);

        chooseFileBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Сохранить файл как");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(extension.toUpperCase(), "*" + extension)
            );

            File initialDir = prepareInitialDirectory(defaultDir);
            if (initialDir != null) {
                fc.setInitialDirectory(initialDir);
            }
            fc.setInitialFileName(selectedFile != null ? selectedFile.getName() : defaultFileName);

            File file = fc.showSaveDialog(owner);
            if (file != null) {
                selectedFile = file;
                pathField.setText(selectedFile.getAbsolutePath());
                pathField.positionCaret(pathField.getText().length());
            }
        });

        pathField.setOnMouseClicked(e -> chooseFileBtn.fire());

        HBox pathRow = new HBox(8, pathCaption, pathField, chooseFileBtn);
        pathRow.setAlignment(Pos.CENTER_LEFT);

        warningLabel.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12;");
        warningLabel.setWrapText(true);
        warningLabel.setMaxWidth(430);
        warningLabel.setManaged(false);
        warningLabel.setVisible(false);

        versionBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            if (newV == 1) {
                updateWarning("Версия 1 не поддерживает номера телефонов и полисы. Эти данные будут безвозвратно утеряны.");
            } else if (newV == 2) {
                updateWarning("Версия 2 не поддерживает полисы. Эти данные будут безвозвратно утеряны.");
            } else {
                updateWarning("");
            }
        });

        VBox content = new VBox(14, versionRow, pathRow, warningLabel);
        content.setPadding(new Insets(16));
        getDialogPane().setContent(content);

        Node okButton = getDialogPane().lookupButton(saveButtonType);
        okButton.setDisable(selectedFile == null);

        pathField.textProperty().addListener((obs, oldVal, newVal) -> {
            okButton.setDisable(selectedFile == null);
        });

        setResultConverter(btn -> {
            if (btn == saveButtonType && selectedFile != null) {
                ensureParentDirectoryExists(selectedFile);
                return new SaveResult(selectedFile, versionBox.getValue());
            }
            return null;
        });
    }

    private Path resolveDefaultDirectory() {
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path documents = userHome.resolve("Documents");
        Path baseDir = Files.isDirectory(documents) ? documents : userHome;
        return baseDir.resolve(APP_FOLDER_NAME);
    }

    private File prepareInitialDirectory(Path defaultDir) {
        try {
            if (selectedFile != null && selectedFile.getParentFile() != null) {
                Path parent = selectedFile.getParentFile().toPath();
                if (!Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                return parent.toFile();
            }
            if (!Files.exists(defaultDir)) {
                Files.createDirectories(defaultDir);
            }
            return defaultDir.toFile();
        } catch (IOException ex) {
            return new File(System.getProperty("user.home"));
        }
    }

    private void ensureParentDirectoryExists(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            try {
                Files.createDirectories(parent.toPath());
            } catch (IOException ignored) {
            }
        }
    }

    private void updateWarning(String text) {
        boolean hasWarning = text != null && !text.isEmpty();
        warningLabel.setText(text);
        warningLabel.setVisible(hasWarning);
        warningLabel.setManaged(hasWarning);

        if (getDialogPane().getScene() != null && getDialogPane().getScene().getWindow() != null) {
            getDialogPane().getScene().getWindow().sizeToScene();
        }
    }
}
