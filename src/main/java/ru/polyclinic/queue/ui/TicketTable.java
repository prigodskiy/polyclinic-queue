package ru.polyclinic.queue.ui;

import java.time.format.DateTimeFormatter;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import ru.polyclinic.queue.model.ClosedTicket;
import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.Editable;

/**
 * Таблица талонов: колонки берутся из модели, без instanceof-цепочек.
 *
 * <p>Один класс используется дважды: обычная таблица очереди и read-only
 * таблица архива ({@code archiveMode = true}) — в архивной версии другое
 * сообщение-заглушка, выделение строк доступно, но кнопки правки отсекаются
 * проверкой {@code Editable} на уровне приложения.
 */
public final class TicketTable extends TableView<Ticket> {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss");

    private final ObservableList<Ticket> rows = FXCollections.observableArrayList();

    public TicketTable() {
        this(false);
    }

    public TicketTable(boolean archiveMode) {
        setItems(rows);
        setPlaceholder(new Label(archiveMode
                ? "Закрытых талонов пока нет"
                : "Очередь пуста"));

        TableColumn<Ticket, String> type = new TableColumn<>("Тип");
        type.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getType().getDisplayName()));

        TableColumn<Ticket, String> card = new TableColumn<>("№ карты");
        card.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getCardNumber()));

        TableColumn<Ticket, String> name = new TableColumn<>("ФИО");
        name.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getFullName()));

        TableColumn<Ticket, String> office = new TableColumn<>("Кабинет");
        office.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getOffice()));

        TableColumn<Ticket, Number> urgency = new TableColumn<>("Срочность");
        urgency.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(data.getValue().getUrgency()));

        TableColumn<Ticket, String> taken = new TableColumn<>("Взято");
        taken.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getTakenAt().format(TIME)));

        TableColumn<Ticket, String> address = new TableColumn<>("Адрес");
        address.setCellValueFactory(data -> {
            Ticket t = data.getValue();
            String value = "";
            if (t instanceof HomeVisitTicket home) {
                value = home.getAddress();
            } else if (t instanceof ClosedTicket closed && closed.getAddressOrNull() != null) {
                value = closed.getAddressOrNull();
            }
            return new ReadOnlyStringWrapper(value);
        });

        getColumns().setAll(java.util.List.of(type, card, name, office, urgency, taken, address));
        // CONSTRAINED_RESIZE_POLICY есть и в JavaFX 17, и в 21 — кросс-версионно.
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY);
        address.setMinWidth(180);
    }

    /** Перерисовать по снимку (GUI не держит ссылку на внутреннее хранилище). */
    public void refresh(Ticket[] snapshot) {
        rows.setAll(snapshot);
    }

    public boolean isEditableSelected() {
        return getSelectionModel().getSelectedItem() instanceof Editable;
    }
}