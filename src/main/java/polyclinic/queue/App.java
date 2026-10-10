package polyclinic.queue;

import javafx.application.Application;

import javafx.geometry.Insets;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

import javafx.stage.Stage;
import javafx.stage.FileChooser;

import javafx.beans.property.SimpleStringProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import polyclinic.queue.datastructure.BinaryMinHeap;
import polyclinic.queue.datastructure.DuplicateTicketException;

import polyclinic.queue.model.HomeVisitTicket;
import polyclinic.queue.model.ClosedTicket;
import polyclinic.queue.model.ClosedHomeVisitTicket;
import polyclinic.queue.model.Ticket;

import polyclinic.queue.gui.EditTicketDialog;
import polyclinic.queue.gui.AddTicketDialog;
import polyclinic.queue.gui.EditHomeVisitDialog;

import polyclinic.queue.io.TicketCsvLoader;
import polyclinic.queue.io.CsvParseException;
import polyclinic.queue.io.WrongFieldCountException;
import polyclinic.queue.io.BadNumberException;
import polyclinic.queue.io.InvalidUrgencyException;
import polyclinic.queue.io.InvalidFullNameException;
import polyclinic.queue.io.InvalidOfficeException;

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

        tableView = new TableView<>();

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

        TableColumn<Ticket, String> officeCol = new TableColumn<>("Кабинет");
        officeCol.setCellValueFactory(new PropertyValueFactory<>("office"));

        TableColumn<Ticket, Integer> urgencyCol = new TableColumn<>("Срочность");
        urgencyCol.setCellValueFactory(new PropertyValueFactory<>("urgency"));

        TableColumn<Ticket, String> typeCol = new TableColumn<>("Тип");
        typeCol.setCellValueFactory(cellData -> {
            Ticket t = cellData.getValue();
            String type;
            if (t instanceof ClosedHomeVisitTicket) {
                type = "Закрытый на дому";
            } else if (t instanceof HomeVisitTicket) {
                type = "На дому";
            } else if (t instanceof ClosedTicket) {
                type = "Закрытый";
            } else {
                type = "Обычный";
            }
            return new SimpleStringProperty(type);
        });
        typeCol.setPrefWidth(100);

        TableColumn<Ticket, String> addressCol = new TableColumn<>("Адрес");
        addressCol.setCellValueFactory(cellData -> {
            Ticket t = cellData.getValue();
            if (t instanceof HomeVisitTicket homeTicket) {
                return new SimpleStringProperty(homeTicket.getAddress());
            }
            return new SimpleStringProperty("");
        });
        addressCol.setPrefWidth(200);

        cardCol.setPrefWidth(100);
        nameCol.setPrefWidth(250);
        officeCol.setPrefWidth(80);
        urgencyCol.setPrefWidth(80);
        typeCol.setPrefWidth(100);
        addressCol.setPrefWidth(250);
        timeCol.setPrefWidth(140);

        tableView.getColumns().addAll(cardCol, nameCol, officeCol, urgencyCol, timeCol, typeCol, addressCol);
        updateTableView();

        Button btnExtract = new Button("Принять пациента");
        btnExtract.setOnAction(e -> handleExtract());

        Button btnAdd = new Button("Добавить пациента");
        btnAdd.setOnAction(e -> handleAdd());

        Button btnEdit = new Button("Изменить талон");
        btnEdit.setOnAction(e -> handleEdit());
        btnEdit.setDisable(true);

        Button btnClose = new Button("Закрыть талон");
        btnClose.setOnAction(e -> handleClose());
        btnClose.setDisable(true);

        Button btnLoad = new Button("Загрузить из CSV");
        btnLoad.setOnAction(e -> handleLoadCsv());

        Button btnSave = new Button("Сохранить в CSV");
        btnSave.setOnAction(e -> handleSaveCsv());

        Button btnMerge = new Button("Добавить из CSV");
        btnMerge.setOnAction(e -> handleMergeCsv());

        HBox csvButtonBox = new HBox(10, btnLoad, btnMerge, btnSave);
        csvButtonBox.setPadding(new Insets(10));

        HBox buttonBox = new HBox(10, btnExtract, btnAdd, btnEdit, btnClose);
        buttonBox.setPadding(new Insets(10));

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            boolean hasSelection = newSelection != null;
            boolean isClosed = newSelection instanceof ClosedTicket || newSelection instanceof ClosedHomeVisitTicket;

            btnEdit.setDisable(!hasSelection || isClosed);
            btnClose.setDisable(!hasSelection || isClosed);
        });

        VBox root = new VBox(10);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        root.getChildren().addAll(tableView, buttonBox, csvButtonBox);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 1200, 600);
        primaryStage.setTitle("Электронная очередь поликлиники");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(500);
        primaryStage.show();
    }

    private void updateTableView() {
        List<Ticket> items = queue.getItemsForUI();

        items.sort((t1, t2) -> {
            int urgencyCompare = Integer.compare(t1.getUrgency(), t2.getUrgency());
            if (urgencyCompare != 0) return urgencyCompare;
            return t1.getTakenAt().compareTo(t2.getTakenAt());
        });

        System.out.println("=== СОСТОЯНИЕ ОЧЕРЕДИ (отсортировано) ===");
        System.out.println("Всего талонов: " + queue.getSize());

        for (int i = 0; i < items.size(); i++) {
            Ticket t = items.get(i);
            String type = t instanceof polyclinic.queue.model.HomeVisitTicket ? "На дому" :
                    t instanceof polyclinic.queue.model.ClosedTicket ? "Закрытый" : "Обычный";

            System.out.printf("[%d] %s | %s | Кабинет: %s | Срочность: %d | Тип: %s%n",
                    i, t.getCardNumber(), t.getFullName(), t.getOffice(), t.getUrgency(), type);
        }
        System.out.println("=========================");

        tableView.getItems().setAll(items);
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
        if (selected == null || selected instanceof ClosedTicket || selected instanceof ClosedHomeVisitTicket) {
            return;
        }

        if (selected instanceof HomeVisitTicket homeTicket) {
            editHomeVisitTicket(homeTicket);
        } else {
            editRegularTicket(selected);
        }
    }

    private void handleClose() {
        Ticket selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null || selected instanceof ClosedTicket || selected instanceof ClosedHomeVisitTicket) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Закрытие талона");
        confirm.setHeaderText("Закрыть талон " + selected.getCardNumber() + "?");
        confirm.setContentText("Талон станет read-only и его нельзя будет изменить.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Ticket oldTicket = findAndRemoveTicket(selected.getCardNumber());
                if (oldTicket != null) {
                    Ticket closed;
                    if (oldTicket instanceof HomeVisitTicket homeTicket) {
                        closed = ClosedHomeVisitTicket.createClosed(
                                homeTicket.getCardNumber(),
                                homeTicket.getFullName(),
                                homeTicket.getOffice(),
                                homeTicket.getUrgency(),
                                homeTicket.getAddress(),
                                homeTicket.getTakenAt()
                        );
                    } else {
                        closed = ClosedTicket.createClosed(
                                oldTicket.getCardNumber(),
                                oldTicket.getFullName(),
                                oldTicket.getOffice(),
                                oldTicket.getUrgency(),
                                oldTicket.getTakenAt()
                        );
                    }
                    queue.insert(closed);
                    showAlert("Талон закрыт", "Талон " + selected.getCardNumber() + " закрыт.");
                    updateTableView();
                }
            }
        });
    }

    private void editRegularTicket(Ticket ticket) {
        EditTicketDialog dialog = new EditTicketDialog(ticket);
        dialog.showAndWait();

        if (dialog.getResult() == ButtonType.OK) {
            String newFullName = dialog.getFullName();
            String newOffice = dialog.getOffice();
            int newUrgency = dialog.getUrgency();

            Ticket oldTicket = findAndRemoveTicket(ticket.getCardNumber());
            if (oldTicket != null) {
                Ticket newTicket = oldTicket.withChanges(newFullName, newOffice, newUrgency);
                queue.insert(newTicket);
                showAlert("Талон изменён", "Талон " + ticket.getCardNumber() + " успешно обновлён.");
                updateTableView();
            }
        }
    }

    private void editHomeVisitTicket(HomeVisitTicket ticket) {
        EditHomeVisitDialog dialog = new EditHomeVisitDialog(ticket);
        dialog.showAndWait();

        if (dialog.getResult() == ButtonType.OK) {
            String newFullName = dialog.getFullName();
            String newOffice = dialog.getOffice();
            int newUrgency = dialog.getUrgency();
            String newAddress = dialog.getAddress();

            Ticket oldTicket = findAndRemoveTicket(ticket.getCardNumber());
            if (oldTicket instanceof HomeVisitTicket oldHomeTicket) {
                HomeVisitTicket newTicket = oldHomeTicket.withChanges(
                        newFullName, newOffice, newUrgency, newAddress);
                queue.insert(newTicket);
                showAlert("Талон изменён",
                        "Талон на дому " + ticket.getCardNumber() + " успешно обновлён.");
                updateTableView();
            }
        }
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
            return;
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

    private void handleMergeCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите CSV-файл для добавления");
        fileChooser.setInitialDirectory(new File("data"));
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV файлы", "*.csv"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file == null) {
            return;
        }

        String filePath = file.getAbsolutePath();

        try {
            List<Ticket> tickets = TicketCsvLoader.loadFromFile(filePath);

            int addedCount = 0;
            int duplicateCount = 0;

            for (Ticket ticket : tickets) {
                try {
                    queue.insert(ticket);
                    addedCount++;
                } catch (DuplicateTicketException e) {
                    duplicateCount++;
                }
            }

            updateTableView();

            String message = "Добавлено талонов: " + addedCount;
            if (duplicateCount > 0) {
                message += "\nПропущено дубликатов: " + duplicateCount;
            }
            message += "\nВсего в очереди: " + queue.getSize();

            showAlert("Добавление завершено", message);

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

    private void handleSaveCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить очередь в CSV-файл");
        fileChooser.setInitialDirectory(new File("data"));
        fileChooser.setInitialFileName("tickets.csv");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV файлы", "*.csv"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file == null) {
            return;
        }

        String filePath = file.getAbsolutePath();

        try {
            TicketCsvLoader.saveToFile(filePath, queue.getItemsForUI());
            showAlert("Сохранение успешно", "Талоны сохранены в файл: " + filePath);
        } catch (IOException e) {
            showAlert("Ошибка записи", "Не удалось записать файл: " + e.getMessage());
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInvalidFullNameError(InvalidFullNameException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка формата ФИО");
        alert.setHeaderText("Код ошибки: " + e.getErrorCode());
        alert.setContentText(String.format(
                "Строка %d: некорректное ФИО.\n" +
                        "Значение: '%s'\n\n" +
                        "ФИО должно содержать 2-3 слова, каждое начинается с заглавной буквы.\n" +
                        "Допускаются буквы, пробелы, дефисы и апострофы.",
                e.getLineNumber(), e.getInvalidValue()));
        alert.showAndWait();
    }

    private void showInvalidOfficeError(InvalidOfficeException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка формата кабинета");
        alert.setHeaderText("Код ошибки: " + e.getErrorCode());
        alert.setContentText(String.format(
                "Строка %d: некорректный номер кабинета.\n" +
                        "Значение: '%s'\n\n" +
                        "Кабинет должен начинаться с цифры и может содержать одну букву (например: 104, 104а, 104-а).",
                e.getLineNumber(), e.getInvalidValue()));
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}