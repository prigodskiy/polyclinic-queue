package ru.polyclinic.queue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ru.polyclinic.queue.datastructure.DuplicateTicketException;
import ru.polyclinic.queue.io.CsvLoadResult;
import ru.polyclinic.queue.io.CsvRowError;
import ru.polyclinic.queue.io.TicketCsvException;
import ru.polyclinic.queue.io.TicketCsvService;
import ru.polyclinic.queue.model.Editable;
import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketType;
import ru.polyclinic.queue.service.QueueService;
import ru.polyclinic.queue.ui.TicketDialog;
import ru.polyclinic.queue.ui.TicketTable;

/**
 * JavaFX-приложение «Электронная очередь поликлиники».
 *
 * <p>Tонкий GUI: все решения — в {@link QueueService} и модели. Здесь только
 * кнопки, таблицы и диалоги. Обработка ошибок — через исключения с кодами из io/
 * и человекочитаемые Alert'ы.
 */
public final class App extends Application {

    private final QueueService service = new QueueService();
    private final TicketCsvService csv = new TicketCsvService();
    private final TicketTable table = new TicketTable();
    private final TicketTable archiveTable = new TicketTable(true);
    private final Label statusLabel = new Label("Очередь: 0 · Архив: 0");
    private TabPane tabs;
    private Button editButton;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setCenter(tabs());
        root.setBottom(footer());

        // Стартуем с демо-данными из файла в репозитории (data/demo_tickets.csv).
        File demo = resolveDemoFile();
        if (demo != null) {
            loadFromSilently(demo);
        }
        refresh();

        Scene scene = new Scene(root, 1040, 620);
        stage.setTitle("Поликлиника — электронная очередь");
        stage.setScene(scene);
        stage.show();
    }

    /** Две вкладки: живая очередь и read-only архив закрытых талонов. */
    private TabPane tabs() {
        Tab queueTab = new Tab("Очередь", table);
        queueTab.setClosable(false);
        Tab archiveTab = new Tab("Закрытые талоны", archiveTable);
        archiveTab.setClosable(false);
        tabs = new TabPane(queueTab, archiveTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }

    /**
     * Нижняя панель, три смысловые группы разделены растяжимыми spacer'ами:
     * 1) принять пациента / создать талон; 2) править/удалить выбранный талон;
     * 3) файловые операции + счётчики очереди и архива.
     */
    private HBox footer() {
        Button accept = new Button("Принять пациента");
        accept.setOnAction(e -> acceptNext());
        Button add = new Button("Создать талон");
        add.setOnAction(e -> addTicket());

        editButton = new Button("Править талон");
        editButton.setOnAction(e -> editSelected());
        Button remove = new Button("Удалить талон");
        remove.setOnAction(e -> removeSelected());

        // Правка доступна только редактируемому талону из живой очереди.
        // Состояние пересчитываем явно на каждое изменение выделения любой
        // таблицы, переключение вкладок и любую перерисовку (refresh) —
        // binding'и молчат, когда строки переставляются местами.
        Runnable updateEditState = this::updateEditButtonState;
        table.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldVal, newVal) -> updateEditState.run());
        archiveTable.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldVal, newVal) -> updateEditState.run());
        tabs.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldVal, newVal) -> updateEditState.run());
        updateEditButtonState();

        Button loadCsv = new Button("Загрузить CSV");
        loadCsv.setOnAction(e -> openFileDialog());
        Button appendCsv = new Button("Добавить CSV");
        appendCsv.setOnAction(e -> mergeFromFile());
        Button saveCsv = new Button("Сохранить CSV");
        saveCsv.setOnAction(e -> saveFile());

        Region spacer1 = new Region();
        Region spacer2 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        HBox box = new HBox(8, accept, add, spacer1,
                editButton, remove, spacer2,
                loadCsv, appendCsv, saveCsv, statusLabel);
        box.setPadding(new Insets(10));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ---------- действия ----------

    private void addTicket() {
        try {
            Optional<Ticket> result = new TicketDialog(null).showAndWait();
            result.ifPresent(ticket -> {
                service.add(ticket);
                refresh();
            });
        } catch (IllegalArgumentException error) {
            showError("Проверьте поля", error.getMessage());
        } catch (DuplicateTicketException error) {
            showError("Дубликат карты", error.getMessage());
        }
    }

    private void editSelected() {
        Ticket selected = requireSelected();
        if (selected == null) {
            return;
        }
        if (!(selected instanceof Editable)) {
            showError("Закрытый талон", "Закрытые талоны доступны только для просмотра.");
            return;
        }
        try {
            Optional<Ticket> result = new TicketDialog(selected).showAndWait();
            result.ifPresent(updated -> {
                service.updateTicket(updated);
                refresh();
            });
        } catch (IllegalArgumentException error) {
            showError("Проверьте поля", error.getMessage());
        }
    }

    private void removeSelected() {
        Ticket selected = requireSelected();
        if (selected == null) {
            return;
        }
        boolean onQueueTab = isQueueTabActive();
        String what = onQueueTab ? "талон из очереди" : "запись из архива";
        Optional<ButtonType> answer = confirm(Alert.AlertType.CONFIRMATION, "Подтверждение удаления",
                "Вы точно хотите удалить %s: карта %s, %s?"
                        .formatted(what, selected.getCardNumber(), selected.getFullName()),
                ButtonType.OK, ButtonType.CANCEL);
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        boolean removed = onQueueTab
                ? service.removeByCardNumber(selected.getCardNumber())
                : service.removeFromArchive(selected.getId());
        if (!removed) {
            showError("Не найдено", "Талон уже удалён.");
        }
        refresh();
    }

    private void acceptNext() {
        if (service.isQueueEmpty()) {
            showError("Очередь пуста", "Нет талонов для обслуживания.");
            return;
        }
        Ticket next = service.acceptNext();
        new Alert(Alert.AlertType.INFORMATION,
                "Принимается: " + next.getFullName()
                        + ", кабинет " + next.getOffice()
                        + ", срочность " + next.getUrgency()).showAndWait();
        refresh();
    }

    private void mergeFromFile() {
        File file = chooseFile("Выберите CSV-файл с талонами для добавления");
        if (file == null) {
            return;
        }
        try {
            CsvLoadResult loaded = csv.load(file.toPath());
            int before = service.queueSize();
            QueueService donor = new QueueService();
            donor.loadAll(loaded.tickets());
            service.merge(donor);
            refresh();
            new Alert(Alert.AlertType.INFORMATION,
                    "Добавлено талонов в очередь: %d (было %d, стало %d).".formatted(
                            loaded.tickets().size(), before, service.queueSize()))
                    .showAndWait();
            warnIfNeeded(loaded);
        } catch (TicketCsvException | DuplicateTicketException error) {
            showError("Не удалось добавить талоны", error.getMessage());
        }
    }

    /** Кнопка «Загрузить CSV»: выбор файла и полная замена очереди. */
    private void openFileDialog() {
        File file = chooseFile("Выберите CSV-файл с талонами");
        if (file == null) {
            return;
        }
        if (!service.isQueueEmpty()) {
            Optional<ButtonType> answer = confirm(Alert.AlertType.CONFIRMATION, "Загрузка из CSV",
                    "Текущая очередь будет заменена содержимым файла. Продолжить?",
                    ButtonType.YES, ButtonType.NO);
            if (answer.isEmpty() || answer.get() != ButtonType.YES) {
                return;
            }
        }
        loadFrom(file);
    }

    /** Загрузка файла: заменяет очередь и архив, показывает предупреждения о битых строках. */
    private void loadFrom(File file) {
        try {
            CsvLoadResult loaded = csv.load(file.toPath());
            service.clearAll();
            service.loadAll(loaded.tickets());
            refresh();
            warnIfNeeded(loaded);
        } catch (TicketCsvException | DuplicateTicketException error) {
            showError("Загрузка не удалась", error.getMessage());
        }
    }

    /** Тихая загрузка стартового демо-файла (без диалогов). */
    private void loadFromSilently(File file) {
        try {
            service.loadAll(csv.load(file.toPath()).tickets());
        } catch (TicketCsvException | DuplicateTicketException error) {
            // стартуем с пустой очередью — это не критично
        }
    }

    private void saveFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("tickets.csv");
        javafx.stage.Window window = table.getScene() != null ? table.getScene().getWindow() : null;
        File file = chooser.showSaveDialog(window);
        if (file == null) {
            return;
        }
        try {
            csv.save(Path.of(file.toURI()), service.exportAll());
        } catch (TicketCsvException error) {
            showError("Сохранение не удалось", error.getMessage());
        }
    }

    // ---------- служебное ----------

    private void refresh() {
        table.refresh(service.snapshot());
        archiveTable.refresh(service.closedSnapshot());
        statusLabel.setText("Очередь: %d · Архив: %d".formatted(
                service.queueSize(), service.archiveSize()));
        updateEditButtonState();
    }

    private File resolveDemoFile() {
        File f = new File("data/demo_tickets.csv");
        if (!f.isFile()) {
            f = new File("../data/demo_tickets.csv");
        }
        return f.isFile() ? f : null;
    }

    /** Выбранный талон в активной вкладке или null + сообщение. */
    private Ticket requireSelected() {
        TicketTable active = isQueueTabActive() ? table : archiveTable;
        Ticket selected = active.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Не выбран талон", "Кликните строку в таблице.");
            return null;
        }
        return selected;
    }

    private boolean isQueueTabActive() {
        return tabs.getSelectionModel().getSelectedIndex() == 0;
    }

    /**
     * Активен ли редактируемый талон: вкладка «Очередь», выделение есть,
     * объект реализует Editable (закрытый талон — нет).
     */
    private void updateEditButtonState() {
        boolean editable = isQueueTabActive() && table.isEditableSelected();
        editButton.setDisable(!editable);
    }

    private File chooseFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        return chooser.showOpenDialog(table.getScene().getWindow());
    }

    /**
     * Предупреждение после загрузки/добавления: сколько строк пропущено и почему.
     * Одна ошибка — одна строка списка (номер строки, код, текст).
     */
    private void warnIfNeeded(CsvLoadResult loaded) {
        List<CsvRowError> errors = loaded.errors();
        if (errors.isEmpty()) {
            return;
        }
        StringBuilder details = new StringBuilder();
        for (CsvRowError error : errors) {
            details.append(error).append('\n');
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Есть пропущенные строки");
        alert.setHeaderText("Загружено корректных: %d, пропущено битых: %d."
                .formatted(loaded.tickets().size(), errors.size()));
        alert.setContentText(details.toString());
        alert.getDialogPane().setPrefWidth(640);
        alert.showAndWait();
    }

    private Optional<ButtonType> confirm(Alert.AlertType type, String header, String message,
                                         ButtonType... buttons) {
        Alert alert = new Alert(type, message, buttons);
        alert.setHeaderText(header);
        return alert.showAndWait();
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}