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

/**
 * Единый диалог «Создать талон» / «Изменить талон».
 *
 * <p>Вёрстка по варианту А: каждый параметр — вертикальный блок
 * «подпись → поле фиксированной ширины → зарезервированный слот ошибки».
 * Слот ошибок существует всегда (managed=true), поэтому окно не прыгает,
 * а текст ошибки привязан ровно к своему полю.
 */
public final class TicketDialog extends Dialog<Ticket> {

    private static final double FIELD_WIDTH = 420;
    private static final double SLOT_HEIGHT = 32;

    private record TypeOption(TicketType type, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private final ComboBox<TypeOption> typeBox = new ComboBox<>(
            javafx.collections.FXCollections.observableArrayList(
                    new TypeOption(TicketType.REGULAR, "Обычный"),
                    new TypeOption(TicketType.HOME_VISIT, "На дому")));
    private final TextField cardField = new TextField();
    private final TextField nameField = new TextField();
    private final TextField officeField = new TextField();
    private final TextField urgencyField = new TextField();
    private final TextField addressField = new TextField();

    private final Label errorCardLabel = new Label();
    private final Label errorNameLabel = new Label();
    private final Label errorOfficeLabel = new Label();
    private final Label errorUrgencyLabel = new Label();
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
        grid.setVgap(6);
        grid.setPadding(new Insets(14));

        typeBox.getSelectionModel().select(startsHome ? 1 : 0);
        typeBox.setDisable(edit);

        cardField.setText(edit ? existing.getCardNumber() : "");
        cardField.setEditable(!edit);
        if (edit) {
            cardField.setStyle("-fx-opacity: 0.6;");
        }
        nameField.setText(edit ? existing.getFullName() : "");
        officeField.setText(edit ? existing.getOffice() : "");
        urgencyField.setText(edit ? Integer.toString(existing.getUrgency()) : "3");
        if (startsHome) {
            addressField.setText(((HomeVisitTicket) existing).getAddress());
        }
        styleErrorLabels(errorCardLabel, errorNameLabel, errorOfficeLabel,
                errorUrgencyLabel, errorAddressLabel);

        int row = 0;
        grid.add(fieldRow("Тип талона:", typeBox, null), 0, row++);
        grid.add(fieldRow("№ карты:", cardField, errorCardLabel), 0, row++);
        grid.add(fieldRow("ФИО:", nameField, errorNameLabel), 0, row++);
        grid.add(fieldRow("Кабинет:", officeField, errorOfficeLabel), 0, row++);
        grid.add(fieldRow("Срочность (0–3):", urgencyField, errorUrgencyLabel), 0, row++);
        grid.add(fieldRow("Адрес:", addressField, errorAddressLabel), 0, row);

        typeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean home = newVal.type() == TicketType.HOME_VISIT;
            addressField.setDisable(!home);
            revalidate(existing, edit);
        });
        addressField.setDisable(!startsHome);

        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(480);

        Node okNode = getDialogPane().lookupButton(saveButtonType);
        okNode.disableProperty().bind(Bindings.createBooleanBinding(
                () -> hasAnyError(existing, edit),
                cardField.textProperty(), nameField.textProperty(), officeField.textProperty(),
                urgencyField.textProperty(), addressField.textProperty(),
                typeBox.valueProperty()));

        for (TextField field : List.of(cardField, nameField, officeField, urgencyField, addressField)) {
            field.textProperty().addListener((obs, oldVal, newVal) -> revalidate(existing, edit));
        }
        revalidate(existing, edit);

        setResultConverter(button -> {
            if (button != saveButtonType) {
                return null;
            }
            return buildTicket(existing, edit);
        });
    }

    private Ticket buildTicket(Ticket existing, boolean edit) {
        boolean home = isHomeSelected();
        long id = edit ? existing.getId() : Ticket.nextIdForUi();
        LocalDateTime takenAt = edit ? existing.getTakenAt() : LocalDateTime.now();
        String card = edit ? existing.getCardNumber() : cardField.getText().strip();
        int urgency = parseUrgencyOrExisting(existing);
        if (home) {
            return new HomeVisitTicket(id, card, nameField.getText().strip(),
                    officeField.getText().strip(), urgency, takenAt,
                    addressField.getText().strip());
        }
        return new Ticket(id, card, nameField.getText().strip(),
                officeField.getText().strip(), urgency, takenAt);
    }

    private int parseUrgencyOrExisting(Ticket existing) {
        try {
            return Integer.parseInt(urgencyField.getText().strip());
        } catch (NumberFormatException e) {
            return existing != null ? existing.getUrgency() : 3;
        }
    }

    private boolean isHomeSelected() {
        return typeBox.getSelectionModel().selectedItemProperty().get().type() == TicketType.HOME_VISIT;
    }

    private boolean hasAnyError(Ticket existing, boolean edit) {
        return !collectErrors(existing, edit).isEmpty();
    }

    private void revalidate(Ticket existing, boolean edit) {
        Map<TicketField, List<String>> errors = collectErrors(existing, edit);
        errorCardLabel.setText(join(errors.get(TicketField.CARD_NUMBER)));
        errorNameLabel.setText(join(errors.get(TicketField.FULL_NAME)));
        errorOfficeLabel.setText(join(errors.get(TicketField.OFFICE)));
        errorUrgencyLabel.setText(join(errors.get(TicketField.URGENCY)));
        errorAddressLabel.setText(isHomeSelected()
                ? join(errors.getOrDefault(TicketField.ADDRESS, List.of()))
                : "");
    }

    private static String join(List<String> list) {
        return list == null ? "" : String.join("\n", list);
    }

    /**
     * Попунктовый сбор ошибок. Каждое поле проверяется НЕЗАВИСИМО от остальных:
     * ошибка в срочности не должна прятать ошибки ФИО или кабинета. Сначала
     * локальная проверка формата срочности, затем — модельная валидация
     * корректно собираемого кандидата (если он собирается вообще).
     */
    private Map<TicketField, List<String>> collectErrors(Ticket existing, boolean edit) {
        String card = edit ? existing.getCardNumber() : cardField.getText().strip();
        String name = nameField.getText().strip();
        String office = officeField.getText().strip();
        String rawUrgency = urgencyField.getText().strip();
        String address = addressField.getText().strip();
        boolean home = isHomeSelected();

        Map<TicketField, List<String>> result = new java.util.EnumMap<>(TicketField.class);

        // 1. Локальная проверка срочности (парсится до создания объекта).
        List<String> urgencyErrors = new java.util.ArrayList<>();
        int urgency;
        try {
            urgency = Integer.parseInt(rawUrgency);
            if (urgency < 0 || urgency > 3) {
                urgencyErrors.add("Срочность должна быть от 0 до 3");
            }
        } catch (NumberFormatException e) {
            urgencyErrors.add("Срочность — целое число от 0 до 3");
            urgency = -1;
        }
        if (!urgencyErrors.isEmpty()) {
            result.put(TicketField.URGENCY, urgencyErrors);
        }

        // 2. Модельная валидация остальных полей — всегда, когда можно собрать
        //    кандидата. Срочность при этом берётся из поля только если она
        //    корректна; иначе подставляем валидное значение-заглушку, чтобы
        //    ошибки ФИО/кабинета/карты всё равно были видны.
        int urgencyForCandidate = (urgency >= 0 && urgency <= 3) ? urgency : 3;
        boolean candidateBuildable = !name.isEmpty() || !office.isEmpty() || !card.isEmpty();
        if (candidateBuildable) {
            Ticket candidate;
            if (home) {
                candidate = new HomeVisitTicket(
                        edit ? existing.getId() : Ticket.nextIdForUi(),
                        card.isBlank() ? "0000" : card,
                        name.isBlank() ? "А Б В" : name,
                        office.isBlank() ? "101" : office,
                        urgencyForCandidate,
                        edit ? existing.getTakenAt() : LocalDateTime.now(),
                        address.isBlank() ? "ул. Пустая, 1" : address);
            } else {
                candidate = new Ticket(
                        edit ? existing.getId() : Ticket.nextIdForUi(),
                        card.isBlank() ? "0000" : card,
                        name.isBlank() ? "А Б В" : name,
                        office.isBlank() ? "101" : office,
                        urgencyForCandidate,
                        edit ? existing.getTakenAt() : LocalDateTime.now());
            }
            Map<TicketField, List<String>> modelErrors = TicketValidator.validateByField(candidate);
            modelErrors.forEach((field, messages) -> {
                // Не переносим модельные ошибки срочности — её уже показал шаг 1.
                if (field == TicketField.URGENCY) {
                    return;
                }
                // Поля, которые пользователь оставил пустыми, кандидат-заглушка
                // сделал валидными — такие ошибки игнорируем, пустые строки
                // покажет отдельная проверка ниже.
                result.merge(field, messages, (a, b) -> {
                    List<String> merged = new java.util.ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                });
            });
        }

        // 3. Пустые обязательные поля и адрес домашнего талона.
        if (card.isEmpty()) {
            put(result, TicketField.CARD_NUMBER, "Номер карты обязателен");
        }
        if (name.isEmpty()) {
            put(result, TicketField.FULL_NAME, "ФИО обязательно");
        }
        if (office.isEmpty()) {
            put(result, TicketField.OFFICE, "Кабинет обязателен");
        }
        if (home && address.isEmpty()) {
            put(result, TicketField.ADDRESS, "Для талона на дому нужен адрес");
        } else if (home && TicketValidator.containsLineBreak(address)) {
            put(result, TicketField.ADDRESS, "Адрес должен занимать одну строку");
        }

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
            label.setMinHeight(SLOT_HEIGHT);
            label.setPrefHeight(SLOT_HEIGHT);
        }
    }

    private VBox fieldRow(String caption, Node input, Label errorSlot) {
        Label captionLabel = new Label(caption);
        captionLabel.setStyle("-fx-font-weight: bold;");
        if (input instanceof TextField tf) {
            tf.setPrefWidth(FIELD_WIDTH);
            tf.setMaxWidth(FIELD_WIDTH);
        } else if (input instanceof ComboBox<?> cb) {
            cb.setPrefWidth(FIELD_WIDTH);
            cb.setMaxWidth(FIELD_WIDTH);
        }
        VBox box = new VBox(2, captionLabel, input);
        if (errorSlot != null) {
            box.getChildren().add(errorSlot);
        }
        VBox.setVgrow(input, Priority.NEVER);
        return box;
    }
}