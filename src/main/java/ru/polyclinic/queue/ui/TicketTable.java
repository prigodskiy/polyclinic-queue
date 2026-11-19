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

public final class TicketTable extends TableView<Ticket> {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss");
    private static final String UNKNOWN_PHONE = "Неизвестен";
    private static final String NO_POLICY = "Нет полиса";

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

        TableColumn<Ticket, String> policy = new TableColumn<>("Полис");
        policy.setCellValueFactory(data -> {
            String rawPolicy = data.getValue().getPolicyNumber();
            if (rawPolicy == null || NO_POLICY.equals(rawPolicy)) {
                return new ReadOnlyStringWrapper("");
            }
            if (rawPolicy.matches("\\d{16}")) {
                String formatted = String.format("%s %s %s %s",
                        rawPolicy.substring(0, 4),
                        rawPolicy.substring(4, 8),
                        rawPolicy.substring(8, 12),
                        rawPolicy.substring(12, 16));
                return new ReadOnlyStringWrapper(formatted);
            }
            return new ReadOnlyStringWrapper(rawPolicy);
        });

        TableColumn<Ticket, String> name = new TableColumn<>("ФИО");
        name.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getFullName()));

        TableColumn<Ticket, String> phone = new TableColumn<>("Телефон");
        phone.setCellValueFactory(data -> {
            String rawPhone = data.getValue().getPhoneNumber();
            if (rawPhone == null || UNKNOWN_PHONE.equals(rawPhone)) {
                return new ReadOnlyStringWrapper("");
            }
            if (rawPhone.matches("\\d{11}")) {
                String formatted = String.format("+%c (%s) %s-%s-%s",
                        rawPhone.charAt(0),
                        rawPhone.substring(1, 4),
                        rawPhone.substring(4, 7),
                        rawPhone.substring(7, 9),
                        rawPhone.substring(9, 11));
                return new ReadOnlyStringWrapper(formatted);
            }
            return new ReadOnlyStringWrapper(rawPhone);
        });

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

        getColumns().setAll(java.util.List.of(type, card, name, policy, phone, office, urgency, taken, address));
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        address.setMinWidth(180);
    }

    public void refresh(Ticket[] snapshot) {
        rows.setAll(snapshot);
    }

    public boolean isEditableSelected() {
        Ticket selected = getSelectionModel().getSelectedItem();
        return selected != null && !(selected instanceof ClosedTicket);
    }
}
