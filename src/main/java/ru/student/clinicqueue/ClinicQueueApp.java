package ru.student.clinicqueue;

import java.io.File;
import java.util.function.Function;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import ru.student.clinicqueue.io.CsvLoadResult;
import ru.student.clinicqueue.io.TicketCsvException;
import ru.student.clinicqueue.io.TicketCsvService;
import ru.student.clinicqueue.model.Editable;
import ru.student.clinicqueue.model.HomeVisitTicket;
import ru.student.clinicqueue.model.Ticket;
import ru.student.clinicqueue.service.ClinicQueue;
import ru.student.clinicqueue.ui.TicketDialog;

public final class ClinicQueueApp extends Application {
    private final ClinicQueue queue = new ClinicQueue();
    private final TicketCsvService csvService = new TicketCsvService();
    private final TableView<Ticket> table = new TableView<>();
    private final Label statusLabel = new Label();
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        configureTable();

        var title = new Label("Электронная очередь поликлиники");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        var subtitle = new Label(
                "Обычные и домашние талончики можно изменять. Закрытые загружаются только из CSV."
        );
        subtitle.setStyle("-fx-text-fill: #5f6368;");

        var header = new VBox(4, title, subtitle);
        header.setPadding(new Insets(20, 20, 12, 20));

        Button addButton = new Button("Добавить");
        Button editButton = new Button("Изменить");
        Button loadButton = new Button("Загрузить CSV");
        Button saveButton = new Button("Сохранить CSV");

        addButton.setDefaultButton(true);
        editButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !(table.getSelectionModel().getSelectedItem() instanceof Editable),
                table.getSelectionModel().selectedItemProperty()
        ));

        addButton.setOnAction(event -> addTicket());
        editButton.setOnAction(event -> editTicket());
        loadButton.setOnAction(event -> loadTickets());
        saveButton.setOnAction(event -> saveTickets());

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var actions = new HBox(10, addButton, editButton, spacer, loadButton, saveButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(12, 20, 12, 20));

        statusLabel.setPadding(new Insets(8, 20, 12, 20));
        statusLabel.setStyle("-fx-text-fill: #5f6368;");

        var root = new BorderPane();
        root.setTop(new VBox(header, actions));
        root.setCenter(table);
        root.setBottom(statusLabel);
        BorderPane.setMargin(table, new Insets(0, 20, 0, 20));

        refreshTable();
        stage.setTitle("Clinic Queue — лабораторная работа №1");
        stage.setMinWidth(950);
        stage.setMinHeight(520);
        stage.setScene(new Scene(root, 1240, 680));
        stage.show();
    }

    private void configureTable() {
        addColumn("Тип", 110, ticket -> ticket.getType().getDisplayName());
        addColumn("№ карты", 125, Ticket::getCardNumber);
        addColumn("ФИО", 190, Ticket::getPatientName);
        addColumn("Кабинет", 120, Ticket::getRoom);
        addColumn("Срочность", 95, Ticket::getUrgencyText);
        addColumn("Время взятия", 150, Ticket::getTakenAtText);
        addColumn("Адрес", 240, ClinicQueueApp::addressForDisplay);
        table.setPlaceholder(new Label("Талоны пока не добавлены"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void addColumn(String title, double minimumWidth, Function<Ticket, String> extractor) {
        var column = new TableColumn<Ticket, String>(title);
        column.setMinWidth(minimumWidth);
        column.setCellValueFactory(data -> new SimpleStringProperty(extractor.apply(data.getValue())));
        table.getColumns().add(column);
    }

    static String addressForDisplay(Ticket ticket) {
        return ticket instanceof HomeVisitTicket homeVisit ? homeVisit.getAddress() : "";
    }

    private void addTicket() {
        TicketDialog.showCreate(stage).ifPresent(ticket -> {
            queue.add(ticket);
            refreshTable();
            table.getSelectionModel().select(ticket);
        });
    }

    private void editTicket() {
        Ticket selected = table.getSelectionModel().getSelectedItem();
        if (!(selected instanceof Editable)) {
            return;
        }

        int index = table.getSelectionModel().getSelectedIndex();
        TicketDialog.showEdit(stage, selected).ifPresent(replacement -> {
            queue.update(index, replacement);
            refreshTable();
            table.getSelectionModel().select(index);
        });
    }

    private void loadTickets() {
        FileChooser chooser = csvChooser("Загрузить талоны из CSV");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        try {
            CsvLoadResult result = csvService.load(file.toPath());
            queue.replaceAll(result.tickets());
            refreshTable();
            if (!result.errors().isEmpty()) {
                showImportWarnings(result);
            }
        } catch (TicketCsvException error) {
            showError("Не удалось загрузить CSV", error.getCode() + ": " + error.getMessage());
        }
    }

    private void saveTickets() {
        FileChooser chooser = csvChooser("Сохранить талоны в CSV");
        chooser.setInitialFileName("tickets.csv");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try {
            csvService.save(file.toPath(), queue.getAll());
            showInformation("Файл сохранён", "Сохранено талонов: " + queue.size());
        } catch (TicketCsvException error) {
            showError("Не удалось сохранить CSV", error.getCode() + ": " + error.getMessage());
        }
    }

    private FileChooser csvChooser(String title) {
        var chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV-файлы", "*.csv"));
        return chooser;
    }

    private void refreshTable() {
        table.getItems().setAll(queue.getAll());
        statusLabel.setText("Талонов: " + queue.size());
    }

    private void showImportWarnings(CsvLoadResult result) {
        var details = new TextArea(
                result.errors().stream()
                        .map(Object::toString)
                        .reduce((left, right) -> left + System.lineSeparator() + right)
                        .orElse("")
        );
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefColumnCount(70);
        details.setPrefRowCount(Math.min(12, result.errors().size() + 1));

        var alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(stage);
        alert.setTitle("CSV загружен с предупреждениями");
        alert.setHeaderText("Загружено: %d, пропущено: %d".formatted(
                result.tickets().size(), result.errors().size()
        ));
        alert.setContentText("Повреждённые строки не были добавлены.");
        alert.getDialogPane().setExpandableContent(details);
        alert.getDialogPane().setExpanded(true);
        alert.showAndWait();
    }

    private void showError(String header, String message) {
        showAlert(Alert.AlertType.ERROR, "Ошибка", header, message);
    }

    private void showInformation(String header, String message) {
        showAlert(Alert.AlertType.INFORMATION, "Готово", header, message);
    }

    private void showAlert(Alert.AlertType type, String title, String header, String message) {
        var alert = new Alert(type);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
