package ru.polyclinic.queue.ui;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import ru.polyclinic.queue.model.HomeVisitTicket;
import ru.polyclinic.queue.model.Ticket;
import ru.polyclinic.queue.model.TicketType;

/**
 * Единый диалог «Создать талон» / «Править талон».
 *
 * <p>Тип выбирается внутри диалога комбобоксом «Обычный / На дому» — вместо
 * двух отдельных кнопок и окон. При смене типа поле «Адрес» появляется или
 * исчезает. Для редактирования тип зафиксирован (нельзя превратить обычный
 * талон в домашний и наоборот), а для закрытых талонов диалог не открывается.
 *
 * <p>Живая валидация: кнопка подтверждения неактивна, пока поля не проходят
 * правила модели ({@code ticket.validate()}); текст ошибок под полями.
 */
public final class TicketDialog extends Dialog<Ticket> {

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
    private final Label addressLabel = new Label("Адрес:");
    private final Label errorLabel = new Label();
    private Tooltip errorTooltip;

    /**
     * @param existing существующий талон (правка) или {@code null} (создание)
     */
    public TicketDialog(Ticket existing) {
        boolean edit = existing != null;
        boolean startsHome = edit && existing instanceof HomeVisitTicket;

        setTitle(edit ? "Изменение талона" : "Создание талона");
        setHeaderText(edit ? "Карта " + existing.getCardNumber() : "Новый талон");

        ButtonType saveButtonType = new ButtonType(
                edit ? "Сохранить" : "Создать", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));

        typeBox.getSelectionModel().select(startsHome ? 1 : 0);
        typeBox.setDisable(edit); // тип фиксирован: правка не меняет класс талона

        cardField.setText(edit ? existing.getCardNumber() : "");
        cardField.setEditable(!edit); // № карты — ключ очереди, не меняется
        if (edit) {
            cardField.setStyle("-fx-opacity: 0.6;");
        }
        nameField.setText(edit ? existing.getFullName() : "");
        officeField.setText(edit ? existing.getOffice() : "");
        urgencyField.setText(edit ? Integer.toString(existing.getUrgency()) : "3");
        if (startsHome) {
            addressField.setText(((HomeVisitTicket) existing).getAddress());
        }

        typeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean home = newVal.type() == TicketType.HOME_VISIT;
            addressField.setVisible(home);
            addressLabel.setVisible(home);
            revalidate(existing, edit);
        });
        addressField.setVisible(startsHome);
        addressLabel.setVisible(startsHome);

        int row = 0;
        addRow(grid, row++, "Тип талона:", typeBox);
        addRow(grid, row++, "№ карты:", cardField);
        addRow(grid, row++, "ФИО:", nameField);
        addRow(grid, row++, "Кабинет:", officeField);
        addRow(grid, row++, "Срочность (0–3):", urgencyField);
        grid.add(addressLabel, 0, row);
        grid.add(addressField, 1, row++);

        errorLabel.setStyle("-fx-text-fill: firebrick;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(470);
        errorLabel.setMaxHeight(58); // длинные списки ошибок не растягивают окно
        grid.add(errorLabel, 0, row, 2, 1);

        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(520); // шире: помещается текст ошибок валидации

        var fields = new javafx.scene.control.TextField[]{cardField, nameField, officeField, urgencyField, addressField};
        for (var field : fields) {
            field.textProperty().addListener((obs, oldValue, newValue) -> revalidate(existing, edit));
        }

        // Кнопка «Сохранить» неактивна, пока данные не проходят проверку модели.
        javafx.scene.Node okNode = getDialogPane().lookupButton(saveButtonType);
        if (okNode instanceof javafx.scene.control.Button okButton) {
            okButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> !currentErrors(existing, edit).isEmpty(),
                    cardField.textProperty(), nameField.textProperty(), officeField.textProperty(),
                    urgencyField.textProperty(), addressField.textProperty(),
                    typeBox.valueProperty()));
        }

        setResultConverter(button -> {
            if (button != saveButtonType) {
                return null;
            }
            return buildFromFields(existing, edit);
        });

        revalidate(existing, edit);
    }

    /** Текущие ошибки введённых данных; пустой список = можно подтверждать. */
    private List<String> currentErrors(Ticket existing, boolean edit) {
        try {
            Ticket candidate = buildFromFields(existing, edit);
            return candidate.validate();
        } catch (RuntimeException expectedWhileTyping) {
            return List.of(expectedWhileTyping.getMessage());
        }
    }

    private void revalidate(Ticket existing, boolean edit) {
        List<String> errors = currentErrors(existing, edit);
        String errorText = errors.isEmpty() ? "" : String.join("\n", errors);

        errorLabel.setText(errorText);

        if (!errors.isEmpty()) {
            if (errorTooltip == null) {
                errorTooltip = new Tooltip(errorText);
                errorTooltip.setWrapText(true);
                errorTooltip.setMaxWidth(400);
                Tooltip.install(errorLabel, errorTooltip);
            } else {
                errorTooltip.setText(errorText);
            }
        } else {
            if (errorTooltip != null) {
                Tooltip.uninstall(errorLabel, errorTooltip);
                errorTooltip = null;
            }
        }
    }

    private Ticket buildFromFields(Ticket existing, boolean edit) {
        boolean home = typeBox.getSelectionModel().selectedItemProperty().get()
                .type() == TicketType.HOME_VISIT;
        String card = cardField.getText().strip();
        String name = nameField.getText().strip();
        String office = officeField.getText().strip();
        String address = addressField.getText().strip();
        int urgency;
        try {
            urgency = Integer.parseInt(urgencyField.getText().strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Срочность — целое число 0–3");
        }
        if (home) {
            return existing != null
                    ? ((HomeVisitTicket) existing).withAddress(name, office, urgency, address.isBlank() ? null : address)
                    : new HomeVisitTicket(card, name, office, urgency, address);
        }
        return existing != null
                ? existing.withChanges(name, office, urgency)
                : new Ticket(card, name, office, urgency);
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node field) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
    }
}