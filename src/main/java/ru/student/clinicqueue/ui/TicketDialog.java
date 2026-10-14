package ru.student.clinicqueue.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import ru.student.clinicqueue.model.Editable;
import ru.student.clinicqueue.model.HomeVisitTicket;
import ru.student.clinicqueue.model.RegularTicket;
import ru.student.clinicqueue.model.Ticket;
import ru.student.clinicqueue.model.TicketType;

public final class TicketDialog {
    private static final DateTimeFormatter FORM_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final Dialog<Ticket> dialog = new Dialog<>();
    private final ComboBox<TicketType> typeBox = new ComboBox<>();
    private final TextField cardNumberField = new TextField();
    private final TextField patientNameField = new TextField();
    private final TextField roomField = new TextField();
    private final TextField urgencyField = new TextField();
    private final TextField takenAtField = new TextField();
    private final TextField addressField = new TextField();
    private final Label addressLabel = new Label("Адрес:");
    private final Label errorLabel = new Label();
    private final ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
    private final ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

    private TicketDialog(Window owner, Ticket ticket) {
        dialog.initOwner(owner);
        dialog.setTitle(ticket == null ? "Добавление талона" : "Изменение талона");
        dialog.setHeaderText(ticket == null ? "Новый редактируемый талон" : ticket.getPatientName());
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        typeBox.setItems(FXCollections.observableArrayList(TicketType.REGULAR, TicketType.HOME_VISIT));
        typeBox.getSelectionModel().select(TicketType.REGULAR);
        typeBox.setMaxWidth(Double.MAX_VALUE);

        cardNumberField.setPromptText("Например, 10004567");
        patientNameField.setPromptText("Например, Анна Смирнова");
        roomField.setPromptText("Например, 101 или Терапевт");
        urgencyField.setPromptText("Целое число от 0 до 3");
        takenAtField.setPromptText("2026-10-12 09:30");
        addressField.setPromptText("Например, ул. Ленина, 15-24");

        if (ticket == null) {
            takenAtField.setText(FORM_TIME.format(LocalDateTime.now()));
        } else {
            if (!(ticket instanceof Editable)) {
                throw new IllegalArgumentException("Закрытый талон доступен только для чтения");
            }
            fillFrom(ticket);
            typeBox.setDisable(true);
        }

        errorLabel.setStyle("-fx-text-fill: #b3261e;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(540);

        var content = new VBox(12, createGrid(), errorLabel);
        content.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(620);

        typeBox.valueProperty().addListener((observable, previous, current) -> updateAddressField());
        updateAddressField();

        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            List<String> errors = validateForm();
            if (!errors.isEmpty()) {
                errorLabel.setText(String.join(System.lineSeparator(), errors));
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == saveButton ? createTicket() : null);
    }

    public static Optional<Ticket> showCreate(Window owner) {
        return new TicketDialog(owner, null).dialog.showAndWait();
    }

    public static Optional<Ticket> showEdit(Window owner, Ticket ticket) {
        return new TicketDialog(owner, ticket).dialog.showAndWait();
    }

    private GridPane createGrid() {
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        var labelColumn = new ColumnConstraints();
        var fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        fieldColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        grid.addRow(0, new Label("Тип:"), typeBox);
        grid.addRow(1, new Label("№ карты:"), cardNumberField);
        grid.addRow(2, new Label("ФИО:"), patientNameField);
        grid.addRow(3, new Label("Кабинет:"), roomField);
        grid.addRow(4, new Label("Срочность:"), urgencyField);
        grid.addRow(5, new Label("Время взятия:"), takenAtField);
        grid.addRow(6, addressLabel, addressField);

        for (Node node : grid.getChildren()) {
            if (node instanceof TextField || node instanceof ComboBox<?>) {
                GridPane.setFillWidth(node, true);
                ((Region) node).setMaxWidth(Double.MAX_VALUE);
            }
        }
        return grid;
    }

    private void fillFrom(Ticket ticket) {
        typeBox.getSelectionModel().select(ticket.getType());
        cardNumberField.setText(ticket.getCardNumber());
        patientNameField.setText(ticket.getPatientName());
        roomField.setText(ticket.getRoom());
        urgencyField.setText(ticket.getUrgencyText());
        takenAtField.setText(FORM_TIME.format(ticket.getTakenAt()));
        addressField.setText(
                ticket instanceof HomeVisitTicket homeVisit ? homeVisit.getAddress() : ""
        );
    }

    private void updateAddressField() {
        boolean homeVisit = typeBox.getValue() == TicketType.HOME_VISIT;
        addressLabel.setVisible(homeVisit);
        addressLabel.setManaged(homeVisit);
        addressField.setVisible(homeVisit);
        addressField.setManaged(homeVisit);
        errorLabel.setText("");

        Platform.runLater(() -> {
            var scene = dialog.getDialogPane().getScene();
            if (scene != null && scene.getWindow() != null) {
                scene.getWindow().sizeToScene();
            }
        });
    }

    private List<String> validateForm() {
        try {
            return ((Editable) createTicket()).validate();
        } catch (IllegalArgumentException error) {
            return List.of(error.getMessage());
        }
    }

    private Ticket createTicket() {
        int urgency;
        try {
            urgency = Integer.parseInt(urgencyField.getText().strip());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Срочность должна быть целым числом");
        }

        LocalDateTime takenAt;
        try {
            takenAt = LocalDateTime.parse(takenAtField.getText().strip(), FORM_TIME);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Время должно иметь формат ГГГГ-ММ-ДД ЧЧ:ММ");
        }

        if (typeBox.getValue() == TicketType.HOME_VISIT) {
            return new HomeVisitTicket(
                    cardNumberField.getText(),
                    patientNameField.getText(),
                    roomField.getText(),
                    urgency,
                    takenAt,
                    addressField.getText()
            );
        }

        return new RegularTicket(
                cardNumberField.getText(),
                patientNameField.getText(),
                roomField.getText(),
                urgency,
                takenAt
        );
    }
}
