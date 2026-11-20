package ru.polyclinic.queue.ui;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketField;
import ru.polyclinic.queue.model.TicketType;
import ru.polyclinic.queue.model.TicketValidator;

public final class TicketDialog extends Dialog<Ticket> {
    private static final double FIELD_WIDTH = 500;
    private static final double SLOT_HEIGHT = 32;
    private static final String UNKNOWN_PHONE = "Неизвестен";
    private static final String NO_POLICY = "Нет полиса";

    private record TypeOption(TicketType type, String displayName) {
        @Override public String toString() { return displayName; }
    }

    private final ComboBox<TypeOption> typeBox = new ComboBox<>(
            javafx.collections.FXCollections.observableArrayList(
                    new TypeOption(TicketType.REGULAR, "Обычный"),
                    new TypeOption(TicketType.HOME_VISIT, "На дому")));
    private final TextField cardField = new TextField();
    private final TextField nameField = new TextField();
    private final TextField officeField = new TextField();
    private final TextField urgencyField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField policyField = new TextField();
    private final TextField addressField = new TextField();

    private final Label errorCardLabel = new Label();
    private final Label errorNameLabel = new Label();
    private final Label errorOfficeLabel = new Label();
    private final Label errorUrgencyLabel = new Label();
    private final Label errorPhoneLabel = new Label();
    private final Label errorPolicyLabel = new Label();
    private final Label errorAddressLabel = new Label();

    public TicketDialog(Ticket existing) {
        boolean edit = existing != null;
        boolean startsHome = edit && existing instanceof HomeVisitTicket;

        setTitle(edit ? "Изменение талона" : "Создание талона");
        setHeaderText(edit ? "Карта " + existing.getCardNumber() : "Новый талон");

        ButtonType saveButtonType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType,
                new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        typeBox.getSelectionModel().select(startsHome ? 1 : 0);
        typeBox.setDisable(edit);

        cardField.setText(edit ? existing.getCardNumber() : "");
        cardField.setEditable(!edit);
        if (edit) cardField.setStyle("-fx-opacity: 0.6;");

        nameField.setText(edit ? existing.getFullName() : "");
        nameField.setPromptText("Иванов Иван Иванович");

        officeField.setText(edit ? existing.getOffice() : "");
        officeField.setPromptText("231, 104-а, 535а");

        urgencyField.setText(edit ? Integer.toString(existing.getUrgency()) : "3");
        urgencyField.setPromptText("3");

        String initialPhone = edit ? existing.getPhoneNumber() : "";
        phoneField.setText(UNKNOWN_PHONE.equals(initialPhone) ? "" : initialPhone);
        phoneField.setPromptText("79001234567");

        String initialPolicy = edit ? existing.getPolicyNumber() : "";
        policyField.setText(NO_POLICY.equals(initialPolicy) ? "" : initialPolicy);
        policyField.setPromptText("4829105736492810");

        if (startsHome) addressField.setText(((HomeVisitTicket) existing).getAddress());

        styleErrorLabels(errorCardLabel, errorNameLabel, errorOfficeLabel, errorUrgencyLabel, errorPhoneLabel, errorPolicyLabel, errorAddressLabel);

        VBox addressRow = fieldRow("Адрес:", addressField, errorAddressLabel);

        int row = 0;
        grid.add(fieldRow("Тип талона:", typeBox, null), 0, row++);
        grid.add(addressRow, 0, row++);
        grid.add(fieldRow("№ карты:", cardField, errorCardLabel), 0, row++);
        grid.add(fieldRow("ФИО:", nameField, errorNameLabel), 0, row++);
        grid.add(fieldRow("Кабинет:", officeField, errorOfficeLabel), 0, row++);
        grid.add(fieldRow("Срочность (0-3):", urgencyField, errorUrgencyLabel), 0, row++);
        grid.add(fieldRow("Телефон (11 цифр):", phoneField, errorPhoneLabel), 0, row++);
        grid.add(fieldRow("Полис (16 цифр):", policyField, errorPolicyLabel), 0, row);

        addressRow.setVisible(startsHome);
        addressRow.setManaged(startsHome);

        typeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isHome = newVal.type() == TicketType.HOME_VISIT;
            addressRow.setVisible(isHome);
            addressRow.setManaged(isHome);
            revalidate(existing, edit);
        });

        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(480);

        Node okNode = getDialogPane().lookupButton(saveButtonType);
        okNode.disableProperty().bind(Bindings.createBooleanBinding(
                () -> hasAnyError(existing, edit),
                cardField.textProperty(), nameField.textProperty(), officeField.textProperty(),
                urgencyField.textProperty(), phoneField.textProperty(), policyField.textProperty(),
                addressField.textProperty(), typeBox.valueProperty()));

        for (TextField field : List.of(cardField, nameField, officeField, urgencyField, phoneField, policyField, addressField)) {
            field.textProperty().addListener((obs, oldVal, newVal) -> revalidate(existing, edit));
        }
        revalidate(existing, edit);

        setResultConverter(button -> button == saveButtonType ? buildTicket(existing, edit) : null);
    }

    private Ticket buildTicket(Ticket existing, boolean edit) {
        boolean home = isHomeSelected();
        long id = edit ? existing.getId() : Ticket.nextIdForUi();
        LocalDateTime takenAt = edit ? existing.getTakenAt() : LocalDateTime.now();
        String card = edit ? existing.getCardNumber() : cardField.getText().strip();
        int urgency = parseUrgencyOrExisting(existing);
        String phone = phoneField.getText().strip();
        String policy = policyField.getText().strip();

        if (home) {
            return new HomeVisitTicket(id, card, nameField.getText().strip(), officeField.getText().strip(),
                    urgency, phone, policy, takenAt, addressField.getText().strip());
        }
        return new Ticket(id, card, nameField.getText().strip(), officeField.getText().strip(),
                urgency, phone, policy, takenAt);
    }

    private int parseUrgencyOrExisting(Ticket existing) {
        try { return Integer.parseInt(urgencyField.getText().strip()); }
        catch (NumberFormatException e) { return existing != null ? existing.getUrgency() : 3; }
    }

    private boolean isHomeSelected() {
        return typeBox.getSelectionModel().selectedItemProperty().get().type() == TicketType.HOME_VISIT;
    }

    private boolean hasAnyError(Ticket existing, boolean edit) { return !collectErrors(existing, edit).isEmpty(); }

    private void revalidate(Ticket existing, boolean edit) {
        Map<TicketField, List<String>> errors = collectErrors(existing, edit);
        setErrorText(errorCardLabel, join(errors.get(TicketField.CARD_NUMBER)));
        setErrorText(errorNameLabel, join(errors.get(TicketField.FULL_NAME)));
        setErrorText(errorOfficeLabel, join(errors.get(TicketField.OFFICE)));
        setErrorText(errorUrgencyLabel, join(errors.get(TicketField.URGENCY)));
        setErrorText(errorPhoneLabel, join(errors.get(TicketField.PHONE_NUMBER)));
        setErrorText(errorPolicyLabel, join(errors.get(TicketField.POLICY_NUMBER)));
        setErrorText(errorAddressLabel, isHomeSelected() ? join(errors.getOrDefault(TicketField.ADDRESS, List.of())) : "");

        if (getDialogPane().getScene() != null && getDialogPane().getScene().getWindow() != null) {
            getDialogPane().getScene().getWindow().sizeToScene();
        }
    }


    private static String join(List<String> list) { return list == null ? "" : String.join("\n", list); }

    private Map<TicketField, List<String>> collectErrors(Ticket existing, boolean edit) {
        String card = edit ? existing.getCardNumber() : cardField.getText().strip();
        String name = nameField.getText().strip();
        String office = officeField.getText().strip();
        String rawUrgency = urgencyField.getText().strip();
        String phone = phoneField.getText().strip();
        String policy = policyField.getText().strip();
        String address = addressField.getText().strip();
        boolean home = isHomeSelected();

        Map<TicketField, List<String>> result = new java.util.EnumMap<>(TicketField.class);

        List<String> urgencyErrors = new java.util.ArrayList<>();
        int urgency;
        try {
            urgency = Integer.parseInt(rawUrgency);
            if (urgency < 0 || urgency > 3) urgencyErrors.add("Срочность должна быть от 0 до 3");
        } catch (NumberFormatException e) {
            urgencyErrors.add("Срочность - целое число от 0 до 3"); urgency = -1;
        }
        if (!urgencyErrors.isEmpty()) result.put(TicketField.URGENCY, urgencyErrors);

        int urgencyForCandidate = (urgency >= 0 && urgency <= 3) ? urgency : 3;
        boolean candidateBuildable = !name.isEmpty() || !office.isEmpty() || !card.isEmpty();
        if (candidateBuildable) {
            Ticket candidate;
            if (home) {
                candidate = new HomeVisitTicket(edit ? existing.getId() : Ticket.nextIdForUi(),
                        card.isBlank() ? "0000" : card, name.isBlank() ? "А Б В" : name,
                        office.isBlank() ? "101" : office, urgencyForCandidate,
                        phone.isBlank() ? "79999999999" : phone, policy.isBlank() ? "1234567890123456" : policy,
                        edit ? existing.getTakenAt() : LocalDateTime.now(), address.isBlank() ? "ул. Пустая, 1" : address);
            } else {
                candidate = new Ticket(edit ? existing.getId() : Ticket.nextIdForUi(),
                        card.isBlank() ? "0000" : card, name.isBlank() ? "А Б В" : name,
                        office.isBlank() ? "101" : office, urgencyForCandidate,
                        phone.isBlank() ? "79999999999" : phone, policy.isBlank() ? "1234567890123456" : policy,
                        edit ? existing.getTakenAt() : LocalDateTime.now());
            }
            Map<TicketField, List<String>> modelErrors = TicketValidator.validateByField(candidate);
            modelErrors.forEach((field, messages) -> {
                if (field == TicketField.URGENCY) return;
                result.merge(field, messages, (a, b) -> {
                    List<String> merged = new java.util.ArrayList<>(a); merged.addAll(b); return merged;
                });
            });
        }

        if (card.isEmpty()) put(result, TicketField.CARD_NUMBER, "Номер карты обязателен");
        if (name.isEmpty()) put(result, TicketField.FULL_NAME, "ФИО обязательно");
        if (office.isEmpty()) put(result, TicketField.OFFICE, "Кабинет обязателен");
        if (home && address.isEmpty()) put(result, TicketField.ADDRESS, "Для талона на дому нужен адрес");
        return result;
    }

    private static void put(Map<TicketField, List<String>> map, TicketField field, String message) {
        map.computeIfAbsent(field, f -> new java.util.ArrayList<>()).add(message);
    }

    private void styleErrorLabels(Label... labels) {
        for (Label label : labels) {
            label.setStyle("-fx-text-fill: #c62828; -fx-font-size: 12;");
            label.setWrapText(true);
            label.setMaxWidth(FIELD_WIDTH);
            label.setVisible(false);
            label.setManaged(false);
        }
    }

    private void setErrorText(Label label, String text) {
        boolean hasError = text != null && !text.isEmpty();
        label.setText(text);
        label.setVisible(hasError);
        label.setManaged(hasError);
    }

    private VBox fieldRow(String caption, Node input, Label errorSlot) {
        Label captionLabel = new Label(caption);
        captionLabel.setStyle("-fx-font-weight: bold;");
        if (input instanceof TextField tf) {
            tf.setPrefWidth(FIELD_WIDTH);
            tf.setMaxWidth(FIELD_WIDTH);
        }
        else if (input instanceof ComboBox<?> cb) {
            cb.setPrefWidth(FIELD_WIDTH);
            cb.setMaxWidth(FIELD_WIDTH);
        }
        VBox box = new VBox(4, captionLabel, input);
        if (errorSlot != null) box.getChildren().add(errorSlot);
        VBox.setVgrow(input, Priority.NEVER);
        return box;
    }
}