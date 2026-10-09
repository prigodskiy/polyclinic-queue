package polyclinic.queue;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.beans.property.SimpleStringProperty;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import polyclinic.queue.datastructure.BinaryMinHeap;

import polyclinic.queue.model.ClosedTicket;
import polyclinic.queue.model.Ticket;

import polyclinic.queue.gui.EditTicketDialog;
import polyclinic.queue.gui.AddTicketDialog;

import polyclinic.queue.io.TicketCsvLoader;
import polyclinic.queue.io.CsvParseException;
import polyclinic.queue.io.WrongFieldCountException;
import polyclinic.queue.io.BadNumberException;
import polyclinic.queue.io.InvalidUrgencyException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;

import java.util.ArrayList;
import java.util.List;

public class App extends Application {

    private BinaryMinHeap queue;
    private TableView<Ticket> tableView;

    @Override
    public void start(Stage primaryStage) {
        queue = new BinaryMinHeap();
        /*queue.insert(new Ticket("123", "Иванов Иван Иванович", 101, 2));
        queue.insert(new Ticket("456", "Петров Пётр Николаевич", 102, 0));
        queue.insert(new Ticket("789", "Сидоров Савелий Фёдорович", 103, 1));*/

        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<Ticket, String> timeCol = new TableColumn<>("Время взятия");
        timeCol.setCellValueFactory(cellData -> {
            LocalDateTime time = cellData.getValue().getTakenAt();
            String formatted = time.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            return new SimpleStringProperty(formatted);
        });
        timeCol.setPrefWidth(150);

        TableColumn<Ticket, String> cardCol = new TableColumn<>("№ карты");
        cardCol.setCellValueFactory(new PropertyValueFactory<>("cardNumber"));

        TableColumn<Ticket, String> nameCol = new TableColumn<>("ФИО");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullName"));

        TableColumn<Ticket, Integer> officeCol = new TableColumn<>("Кабинет");
        officeCol.setCellValueFactory(new PropertyValueFactory<>("office"));

        TableColumn<Ticket, Integer> urgencyCol = new TableColumn<>("Срочность");
        urgencyCol.setCellValueFactory(new PropertyValueFactory<>("urgency"));

        tableView.getColumns().addAll(cardCol, nameCol, officeCol, urgencyCol, timeCol);
        updateTableView();

        Button btnExtract = new Button("Принять пациента");
        btnExtract.setOnAction(e -> handleExtract());

        Button btnAdd = new Button("Добавить пациента");
        btnAdd.setOnAction(e -> handleAdd());

        Button btnEdit = new Button("Изменить талон");
        btnEdit.setOnAction(e -> handleEdit());
        btnEdit.setDisable(true);

        Button btnLoad = new Button("Загрузить из CSV");
        btnLoad.setOnAction(e -> handleLoadCsv());

        Button btnSave = new Button("Сохранить в CSV");
        btnSave.setOnAction(e -> handleSaveCsv());

        HBox csvButtonBox = new HBox(10, btnLoad, btnSave);
        csvButtonBox.setPadding(new Insets(10));

        HBox buttonBox = new HBox(10, btnExtract, btnAdd, btnEdit);
        buttonBox.setPadding(new Insets(10));

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null && !(newSelection instanceof ClosedTicket)) {
                btnEdit.setDisable(false);
            } else {
                btnEdit.setDisable(true);
            }
        });

        VBox root = new VBox(10, tableView, buttonBox, csvButtonBox);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Электронная очередь поликлиники");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void updateTableView() {
        tableView.getItems().setAll(queue.getItemsForUI());
    }

    private void handleExtract() {
        if (queue.isEmpty()) {
            showAlert("Очередь пуста", "Нет пациентов для приема.");
            return;
        }
        Ticket min = queue.extractMin();
        showAlert("Пациент принят", "Принят: " + min.getFullName() + " (Срочность: " + min.getUrgency() + ")");
        updateTableView();
    }

    private void handleAdd() {
        AddTicketDialog dialog = new AddTicketDialog();
        dialog.showAndWait().ifPresent(newTicket -> {
            queue.insert(newTicket);
            showAlert("Талон добавлен", "Талон " + newTicket.getCardNumber() + " успешно добавлен в очередь.");
            updateTableView();
        });
    }

    private void handleEdit() {
        Ticket selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        EditTicketDialog dialog = new EditTicketDialog(selected);
        dialog.showAndWait().ifPresent(result -> {
            String newFullName = dialog.getFullName();
            int newOffice = dialog.getOffice();
            int newUrgency = dialog.getUrgency();

            Ticket oldTicket = findAndRemoveTicket(selected.getCardNumber());

            if (oldTicket != null) {
                Ticket newTicket = oldTicket.withChanges(newFullName, newOffice, newUrgency);
                queue.insert(newTicket);
                showAlert("Талон изменён", "Талон " + selected.getCardNumber() + " успешно обновлён.");
                updateTableView();
            }
        });
    }

    private Ticket findAndRemoveTicket(String cardNumber) {
        List<Ticket> temp = new ArrayList<>();
        Ticket found = null;

        while (!queue.isEmpty()) {
            Ticket t = queue.extractMin();
            if (t.getCardNumber().equals(cardNumber)) {
                found = t;
            } else {
                temp.add(t);
            }
        }

        for (Ticket t : temp) {
            queue.insert(t);
        }

        return found;
    }

    private void handleLoadCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите CSV-файл");
        fileChooser.setInitialDirectory(new File("data"));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV файлы", "*.csv"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file == null) {
            return; // Пользователь отменил выбор
        }

        String filePath = file.getAbsolutePath();

        try {
            List<Ticket> tickets = TicketCsvLoader.loadFromFile(filePath);

            queue.clear();
            for (Ticket ticket : tickets) {
                queue.insert(ticket);
            }

            updateTableView();
            showAlert("Загрузка успешна", "Загружено талонов: " + tickets.size());

        } catch (FileNotFoundException e) {
            showFileNotFoundError(filePath);
        } catch (WrongFieldCountException e) {
            showWrongFieldCountError(e);
        } catch (BadNumberException e) {
            showBadNumberError(e);
        } catch (InvalidUrgencyException e) {
            showInvalidUrgencyError(e);
        } catch (CsvParseException e) {
            showGenericCsvError(e);
        } catch (IOException e) {
            showAlert("Ошибка чтения", "Не удалось прочитать файл: " + e.getMessage());
        }
    }

    private void showFileNotFoundError(String filePath) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Файл не найден");
        alert.setHeaderText("CSV-файл отсутствует");
        alert.setContentText("Файл '" + filePath + "' не найден.\n" +
                "Создайте файл или выберите другой путь.");
        alert.showAndWait();
    }

    private void showWrongFieldCountError(WrongFieldCountException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка формата CSV");
        alert.setHeaderText("Код ошибки: " + e.getErrorCode());
        alert.setContentText(String.format(
                "Строка %d: неправильное количество полей.\n" +
                        "Ожидается: %d полей\n" +
                        "Найдено: %d полей\n\n" +
                        "Проверьте, что все строки имеют формат:\n" +
                        "cardNumber,fullName,office,urgency,takenAt",
                e.getLineNumber(), e.getExpectedCount(), e.getActualCount()));
        alert.showAndWait();
    }

    private void showBadNumberError(BadNumberException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка формата числа");
        alert.setHeaderText("Код ошибки: " + e.getErrorCode());
        alert.setContentText(String.format(
                "Строка %d: неверный формат числа в поле '%s'.\n" +
                        "Значение: '%s'\n\n" +
                        "Поле '%s' должно содержать целое число.",
                e.getLineNumber(), e.getFieldName(), e.getInvalidValue(), e.getFieldName()));
        alert.showAndWait();
    }

    private void showInvalidUrgencyError(InvalidUrgencyException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Недопустимая срочность");
        alert.setHeaderText("Код ошибки: " + e.getErrorCode());
        alert.setContentText(String.format(
                "Строка %d: срочность должна быть в диапазоне 0-3.\n" +
                        "Получено значение: %d\n\n" +
                        "0 — экстренный\n" +
                        "1 — срочный\n" +
                        "2 — плановый\n" +
                        "3 — отложенный",
                e.getLineNumber(), e.getInvalidValue()));
        alert.showAndWait();
    }

    private void showGenericCsvError(CsvParseException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка парсинга CSV");
        alert.setHeaderText("Код ошибки: " + e.getErrorCode());
        alert.setContentText(String.format(
                "Строка %d: %s\n\n" +
                        "Проверьте формат файла.",
                e.getLineNumber(), e.getMessage()));
        alert.showAndWait();
    }

    private void handleSaveCsv() {
        String filePath = "data/tickets.csv";  // ← изменил путь

        try {
            TicketCsvLoader.saveToFile(filePath, queue.getItemsForUI());
            showAlert("Сохранение успешно", "Талоны сохранены в файл: " + filePath);
        } catch (IOException e) {
            showAlert("Ошибка записи", "Не удалось записать файл: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}