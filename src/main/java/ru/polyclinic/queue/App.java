package ru.polyclinic.queue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
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
import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.Editable;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.service.QueueService;
import ru.polyclinic.queue.service.TicketGenerator;
import ru.polyclinic.queue.service.Benchmark;
import ru.polyclinic.queue.service.ConcurrencyDemo;
import ru.polyclinic.queue.ui.TicketDialog;
import ru.polyclinic.queue.ui.TicketTable;

public final class App extends Application {

    private final QueueService service = new QueueService();
    private final TicketCsvService csv = new TicketCsvService();
    private final TicketTable table = new TicketTable();
    private final TicketTable archiveTable = new TicketTable(true);
    private final Label statusLabel = new Label("Очередь: 0 · Архив: 0");

    private ProgressBar progressBar;
    private Button cancelButton;
    private Task<?> currentTask;
    private Thread currentThread;
    private boolean backgroundBusy = false;
    private volatile boolean cancelled = false;
    private volatile boolean addPhase = false; // Флаг фазы добавления

    private TabPane tabs;
    private Button editButton;

    public record AddResult(int added, int archived, int duplicates, boolean cancelled) {}

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setCenter(tabs());
        root.setBottom(footer());

        File demo = resolveDemoFile();
        if (demo != null) {
            loadFromSilently(demo);
        }
        refresh();

        Scene scene = new Scene(root, 1040, 620);
        stage.setTitle("Электронная очередь поликлиники");
        stage.setScene(scene);
        stage.show();
    }

    private TabPane tabs() {
        Tab queueTab = new Tab("Очередь", table);
        queueTab.setClosable(false);
        Tab archiveTab = new Tab("Закрытые талоны", archiveTable);
        archiveTab.setClosable(false);
        tabs = new TabPane(queueTab, archiveTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }

    private HBox footer() {
        Button accept = new Button("Принять пациента");
        accept.setOnAction(e -> acceptNext());
        Button add = new Button("Создать талон");
        add.setOnAction(e -> addTicket());
        Button generate = new Button("Сгенерировать талоны");
        generate.setOnAction(e -> generateTickets());

        editButton = new Button("Править талон");
        editButton.setOnAction(e -> editSelected());
        Button remove = new Button("Удалить талон");
        remove.setOnAction(e -> removeSelected());

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
        Button benchmarkBtn = new Button("Бенчмарк");
        benchmarkBtn.setOnAction(e -> {
            if (backgroundBusy) { showError("Занято", "Подождите"); return; }

            backgroundBusy = true;
            benchmarkBtn.setDisable(true);
            System.out.println("[БЕНЧМАРК] Запуск... (может занять 15-30 секунд)");

            Task<String> task = new Task<>() {
                @Override
                protected String call() {
                    return Benchmark.run();
                }
            };

            task.setOnSucceeded(evt -> {
                System.out.println("[БЕНЧМАРК] Готово.");
                backgroundBusy = false;
                benchmarkBtn.setDisable(false);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, task.getValue());
                alert.setTitle("Замеры производительности");
                alert.setHeaderText("Сравнение структур данных");
                alert.getDialogPane().setPrefWidth(700);
                alert.showAndWait();
            });

            // ЭТОГО НЕ ХВАТАЛО — без него ошибки молчат
            task.setOnFailed(evt -> {
                System.out.println("[БЕНЧМАРК] ОШИБКА!");
                Throwable ex = task.getException();
                if (ex != null) ex.printStackTrace();
                backgroundBusy = false;
                benchmarkBtn.setDisable(false);
                showError("Ошибка бенчмарка",
                        ex != null ? ex.toString() : "Неизвестная ошибка");
            });

            new Thread(task).start();
        });

        Button raceBtn = new Button("Демо-гонки");
        raceBtn.setOnAction(e -> {
            System.out.println("[ГОНКИ] Запуск...");
            try {
                String result = ConcurrencyDemo.demonstrate();
                System.out.println(result);
                Alert alert = new Alert(Alert.AlertType.WARNING, result);
                alert.setTitle("Многопоточность");
                alert.setHeaderText("Демонстрация защиты данных");
                alert.getDialogPane().setPrefWidth(500);
                alert.showAndWait();
            } catch (Exception ex) {
                ex.printStackTrace();
                showError("Ошибка демо-гонок", ex.toString());
            }
        });

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(150);
        progressBar.setVisible(false);

        cancelButton = new Button("Отмена");
        cancelButton.setVisible(false);
        cancelButton.setOnAction(e -> cancelBackgroundTask());

        Region spacer1 = new Region();
        Region spacer2 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        HBox box = new HBox(8, accept, add, generate, spacer1,
                editButton, remove, spacer2,
                progressBar, cancelButton, benchmarkBtn, raceBtn, loadCsv, appendCsv, saveCsv, statusLabel);
        box.setPadding(new Insets(10));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void addTicket() {
        try {
            Optional<Ticket> result = new TicketDialog(null).showAndWait();
            result.ifPresent(ticket -> {
                service.add(ticket);
                service.printStructure();
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
        if (selected == null) return;
        if (!(selected instanceof Editable)) {
            showError("Закрытый талон", "Закрытые талоны доступны только для просмотра.");
            return;
        }
        try {
            Optional<Ticket> result = new TicketDialog(selected).showAndWait();
            result.ifPresent(updated -> {
                service.updateTicket(updated);
                service.printStructure();
                refresh();
            });
        } catch (IllegalArgumentException error) {
            showError("Проверьте поля", error.getMessage());
        }
    }

    private void removeSelected() {
        Ticket selected = requireSelected();
        if (selected == null) return;
        boolean onQueueTab = isQueueTabActive();
        String what = onQueueTab ? "талон из очереди" : "запись из архива";
        Optional<ButtonType> answer = confirm(Alert.AlertType.CONFIRMATION, "Подтверждение удаления",
                "Вы точно хотите удалить %s: карта %s, %s?"
                        .formatted(what, selected.getCardNumber(), selected.getFullName()),
                ButtonType.OK, ButtonType.CANCEL);
        if (answer.isEmpty() || answer.get() != ButtonType.OK) return;

        boolean removed = onQueueTab
                ? service.removeByCardNumber(selected.getCardNumber())
                : service.removeFromArchive(selected.getId());
        if (!removed) {
            showError("Не найдено", "Талон уже удалён.");
        }
        service.printStructure();
        refresh();
    }

    private void acceptNext() {
        if (service.isQueueEmpty()) {
            showError("Очередь пуста", "Нет талонов для обслуживания.");
            return;
        }
        Ticket next = service.acceptNext();
        service.printStructure();
        new Alert(Alert.AlertType.INFORMATION,
                "Принимается: " + next.getFullName()
                        + ", кабинет " + next.getOffice()
                        + ", срочность " + next.getUrgency()).showAndWait();
        refresh();
    }

    // ═══════════════════════════════════════════════════════════════
    // ГЕНЕРАЦИЯ ТАЛОНОВ
    // ═══════════════════════════════════════════════════════════════

    private void generateTickets() {
        if (backgroundBusy) {
            showError("Занято", "Дождитесь завершения текущей операции.");
            return;
        }

        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("10");
        dialog.setTitle("Генерация талонов");
        dialog.setHeaderText("Сколько талонов сгенерировать?");
        dialog.setContentText("Количество (1 — 100 000):");

        dialog.showAndWait().ifPresent(input -> {
            try {
                int count = Integer.parseInt(input.strip());
                if (count <= 0 || count > 100_000) {
                    showError("Неверное число", "Введите от 1 до 100 000.");
                    return;
                }
                generateInBackground(count);
            } catch (NumberFormatException ex) {
                showError("Ошибка ввода", "Введите целое число.");
            }
        });
    }

    private void generateInBackground(int count) {
        cancelled = false;
        addPhase = false;
        backgroundBusy = true;
        setButtonsDisabled(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        cancelButton.setVisible(true);

        Task<Ticket[]> task = new Task<Ticket[]>() {
            @Override
            protected Ticket[] call() {
                TicketGenerator generator = new TicketGenerator();
                return generator.generate(count, (current, total) -> {
                    updateProgress(current, total);
                });
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        currentTask = task;

        task.setOnSucceeded(e -> {
            if (cancelled) {
                System.out.println("[ОТМЕНА] Генерация отменена - талоны НЕ добавлены");
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Генерация отменена.").showAndWait();
                    cleanup();
                });
                return;
            }

            Ticket[] tickets = task.getValue();
            if (tickets == null || tickets.length == 0) {
                cleanup();
                return;
            }

            // Переходим к фазе добавления
            addAllTicketsInBackground(tickets, count);
        });

        task.setOnFailed(e -> {
            if (cancelled) {
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Генерация отменена.").showAndWait();
                    cleanup();
                });
                return;
            }
            Throwable ex = task.getException();
            String msg = (ex != null) ? ex.getMessage() : "Неизвестная ошибка";
            Platform.runLater(() -> {
                showError("Ошибка генерации", msg);
                cleanup();
            });
        });

        task.setOnCancelled(e -> {
            cancelled = true;
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.INFORMATION, "Генерация отменена.").showAndWait();
                cleanup();
            });
        });

        currentThread = new Thread(task);
        currentThread.setDaemon(true);
        currentThread.start();
    }

    // ═══════════════════════════════════════════════════════════════
    // ЗАГРУЗКА CSV
    // ═══════════════════════════════════════════════════════════════

    private void openFileDialog() {
        if (backgroundBusy) {
            showError("Занято", "Дождитесь завершения текущей операции.");
            return;
        }

        File file = chooseFile("Выберите CSV-файл с талонами");
        if (file == null) return;

        if (!service.isQueueEmpty()) {
            Optional<ButtonType> answer = confirm(Alert.AlertType.CONFIRMATION, "Загрузка из CSV",
                    "Текущая очередь будет заменена содержимым файла. Продолжить?",
                    ButtonType.YES, ButtonType.NO);
            if (answer.isEmpty() || answer.get() != ButtonType.YES) return;
        }

        loadInBackground(file, true);
    }

    private void mergeFromFile() {
        if (backgroundBusy) {
            showError("Занято", "Дождитесь завершения текущей операции.");
            return;
        }

        File file = chooseFile("Выберите CSV-файл с талонами для добавления");
        if (file == null) return;

        loadInBackground(file, false);
    }

    private void loadInBackground(File file, boolean replace) {
        cancelled = false;
        addPhase = false;
        backgroundBusy = true;
        setButtonsDisabled(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        cancelButton.setVisible(true);

        Task<CsvLoadResult> task = new Task<CsvLoadResult>() {
            @Override
            protected CsvLoadResult call() throws Exception {
                return csv.load(file.toPath(), (current, total) -> {
                    updateProgress(current, total);
                });
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        currentTask = task;

        task.setOnSucceeded(e -> {
            if (cancelled) {
                System.out.println("[ОТМЕНА] Загрузка отменена - талоны НЕ добавлены");
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Загрузка отменена.").showAndWait();
                    cleanup();
                });
                return;
            }

            CsvLoadResult loaded = task.getValue();
            if (loaded == null || loaded.tickets().isEmpty()) {
                cleanup();
                return;
            }

            if (replace) {
                service.clearAll();
            }

            // Переходим к фазе добавления
            addAllTicketsInBackground(loaded.tickets().toArray(new Ticket[0]), loaded.tickets().size());
            warnIfNeeded(loaded);
        });

        task.setOnFailed(e -> {
            if (cancelled) {
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Загрузка отменена.").showAndWait();
                    cleanup();
                });
                return;
            }
            Throwable ex = task.getException();
            String msg = (ex != null) ? ex.getMessage() : "Неизвестная ошибка";
            Platform.runLater(() -> {
                showError("Ошибка загрузки", msg);
                cleanup();
            });
        });

        task.setOnCancelled(e -> {
            cancelled = true;
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.INFORMATION, "Загрузка отменена.").showAndWait();
                cleanup();
            });
        });

        currentThread = new Thread(task);
        currentThread.setDaemon(true);
        currentThread.start();
    }

    // ═══════════════════════════════════════════════════════════════
    // ДОБАВЛЕНИЕ ТАЛОНОВ В ФОНЕ (С ОТКАТОМ ПРИ ОТМЕНЕ)
    // ═══════════════════════════════════════════════════════════════

    private void addAllTicketsInBackground(Ticket[] tickets, int totalCount) {
        if (cancelled) {
            cleanup();
            return;
        }

        addPhase = true;

        Task<AddResult> addTask = new Task<>() {
            @Override
            protected AddResult call() {
                List<Ticket> addedQueue = new ArrayList<>();
                List<ClosedTicket> addedArchive = new ArrayList<>();
                int duplicates = 0;

                for (int i = 0; i < tickets.length; i++) {
                    if (cancelled) {
                        // ОТКАТ
                        for (Ticket t : addedQueue) service.removeByCardNumber(t.getCardNumber());
                        for (ClosedTicket c : addedArchive) service.removeFromArchive(c.getId());
                        return new AddResult(0, 0, 0, true);
                    }

                    Ticket t = tickets[i];
                    if (t instanceof ClosedTicket closed) {
                        service.addToArchive(closed);
                        addedArchive.add(closed);
                    } else {
                        try {
                            service.add(t);
                            addedQueue.add(t);
                        } catch (DuplicateTicketException e) {
                            duplicates++; // Больше не проглатываем молча!
                        }
                    }

                    if (i % 500 == 0 || i == tickets.length - 1) {
                        updateProgress(i + 1, tickets.length);
                    }
                }
                return new AddResult(addedQueue.size(), addedArchive.size(), duplicates, false);
            }
        };

        progressBar.progressProperty().unbind();
        progressBar.progressProperty().bind(addTask.progressProperty());
        currentTask = addTask;

        addTask.setOnSucceeded(e -> {
            addPhase = false;
            AddResult result = addTask.getValue();
            service.printStructure();

            Platform.runLater(() -> {
                refresh();
                if (result.cancelled()) {
                    new Alert(Alert.AlertType.INFORMATION, "Добавление отменено. Очередь не изменена.").showAndWait();
                } else {
                    String msg = "Добавлено: " + result.added() + " из " + totalCount;
                    if (result.archived() > 0) msg += " (+" + result.archived() + " в архив)";
                    if (result.duplicates() > 0) msg += ". Пропущено дубликатов: " + result.duplicates();
                    new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
                }
                cleanup();
            });
        });

        addTask.setOnFailed(e -> {
            addPhase = false;
            Platform.runLater(() -> {
                showError("Ошибка добавления", addTask.getException() != null ? addTask.getException().getMessage() : "Ошибка");
                cleanup();
            });
        });

        currentThread = new Thread(addTask);
        currentThread.setDaemon(true);
        currentThread.start();
    }

    // ═══════════════════════════════════════════════════════════════
    // ОТМЕНА И ОЧИСТКА
    // ═══════════════════════════════════════════════════════════════

    private void cancelBackgroundTask() {
        System.out.println("[ОТМЕНА] cancelBackgroundTask() вызван");
        cancelled = true;

        // Если мы в фазе добавления, мы НЕ прерываем поток жестко,
        // чтобы он успел откатить изменения. Он сам завершится, увидев cancelled = true.
        if (!addPhase) {
            if (currentTask != null) {
                currentTask.cancel();
            }
            if (currentThread != null) {
                currentThread.interrupt();
            }
        }
    }

    private void cleanup() {
        if (progressBar != null) {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            progressBar.setVisible(false);
        }
        if (cancelButton != null) {
            cancelButton.setVisible(false);
        }
        backgroundBusy = false;
        addPhase = false; // Сбрасываем флаг
        setButtonsDisabled(false);
        currentTask = null;
        currentThread = null;
    }

    private void setButtonsDisabled(boolean disabled) {
        if (table.getScene() == null) return;
        Node[] buttons = table.getScene().getRoot()
                .lookupAll(".button").toArray(new Node[0]);
        for (Node node : buttons) {
            if (node instanceof Button btn && btn != cancelButton) {
                btn.setDisable(disabled);
            }
        }
    }

    private void saveFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("tickets.csv");
        javafx.stage.Window window = table.getScene() != null ? table.getScene().getWindow() : null;
        File file = chooser.showSaveDialog(window);
        if (file == null) return;
        try {
            csv.save(Path.of(file.toURI()), service.exportAll());
        } catch (TicketCsvException error) {
            showError("Сохранение не удалось", error.getMessage());
        }
    }

    private void refresh() {
        table.refresh(service.snapshot());
        archiveTable.refresh(service.closedSnapshot());
        statusLabel.setText("Очередь: %d · Архив: %d".formatted(
                service.queueSize(), service.archiveSize()));
        updateEditButtonState();
    }

    private File resolveDemoFile() {
        File f = new File("data/demo_tickets.csv");
        if (!f.isFile()) f = new File("../data/demo_tickets.csv");
        return f.isFile() ? f : null;
    }

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

    private void updateEditButtonState() {
        if (editButton == null) return;
        boolean editable = isQueueTabActive() && table.isEditableSelected();
        editButton.setDisable(!editable);
    }

    private File chooseFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        return chooser.showOpenDialog(table.getScene().getWindow());
    }

    private void warnIfNeeded(CsvLoadResult loaded) {
        List<CsvRowError> errors = loaded.errors();
        if (errors.isEmpty()) return;

        StringBuilder details = new StringBuilder();
        for (CsvRowError error : errors) {
            details.append(error).append('\n');
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Есть пропущенные строки");
        alert.setHeaderText("Загружено: %d, пропущено: %d."
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

    private void loadFromSilently(File file) {
        try {
            service.loadAll(csv.load(file.toPath()).tickets());
            service.printStructure();
        } catch (TicketCsvException | DuplicateTicketException error) {
            // стартуем с пустой очередью
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}