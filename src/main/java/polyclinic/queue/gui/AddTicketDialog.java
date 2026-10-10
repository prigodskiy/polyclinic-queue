package polyclinic.queue.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import polyclinic.queue.model.Ticket;
import polyclinic.queue.model.HomeVisitTicket;
import polyclinic.queue.model.ClosedTicket;
import polyclinic.queue.util.Validators;

import java.time.LocalDateTime;

public class AddTicketDialog extends Dialog<Ticket> {

    private TextField cardNumberField;
    private TextField fullNameField;
    private TextField officeField;
    private ComboBox<Integer> urgencyComboBox;
    private ComboBox<String> typeComboBox;
    private TextField addressField;
    private GridPane grid;
    private final ButtonType okButtonType;

    public AddTicketDialog() {
        setTitle("Добавление нового талона");
        setHeaderText("Введите данные пациента");
        setResizable(true);
        getDialogPane().setMinSize(500, 350);
        getDialogPane().setPrefSize(500, 350);

        okButtonType = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        cardNumberField = new TextField();
        cardNumberField.setPromptText("2305");

        fullNameField = new TextField();
        fullNameField.setPromptText("Иванов Иван Иванович");

        officeField = new TextField();
        officeField.setPromptText("104 (или 104а или 104-а)");

        urgencyComboBox = new ComboBox<>();
        urgencyComboBox.getItems().addAll(0, 1, 2, 3);
        urgencyComboBox.setValue(1);

        typeComboBox = new ComboBox<>();
        typeComboBox.getItems().addAll("Обычный", "На дому", "Закрытый");
        typeComboBox.setValue("Обычный");
        typeComboBox.setOnAction(e -> buildForm());

        addressField = new TextField();
        addressField.setPromptText("Адрес вызова");

        cardNumberField.textProperty().addListener((obs, old, newVal) -> validate());
        fullNameField.textProperty().addListener((obs, old, newVal) -> validate());
        officeField.textProperty().addListener((obs, old, newVal) -> validate());
        addressField.textProperty().addListener((obs, old, newVal) -> validate());

        buildForm();

        getDialogPane().setContent(grid);
        cardNumberField.requestFocus();

        setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return createTicket();
            }
            return null;
        });
    }

    private void buildForm() {
        grid.getChildren().clear();
        grid.setMinWidth(450);
        grid.setMinHeight(280);

        String cabinetLabel = "На дому".equals(typeComboBox.getValue())
                ? "Участок/Кабинет врача:"
                : "Кабинет:";

        grid.add(new Label("№ карты:"), 0, 0);
        grid.add(cardNumberField, 1, 0);
        grid.add(new Label("ФИО:"), 0, 1);
        grid.add(fullNameField, 1, 1);
        grid.add(new Label(cabinetLabel), 0, 2);
        grid.add(officeField, 1, 2);
        grid.add(new Label("Срочность:"), 0, 3);
        grid.add(urgencyComboBox, 1, 3);
        grid.add(new Label("Тип:"), 0, 4);
        grid.add(typeComboBox, 1, 4);

        if ("На дому".equals(typeComboBox.getValue())) {
            grid.add(new Label("Адрес:"), 0, 5);
            grid.add(addressField, 1, 5);
        }

        validate();
    }

    private void validate() {
        String cardNumber = cardNumberField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String officeText = officeField.getText().trim();
        String address = addressField.getText().trim();
        String type = typeComboBox.getValue();

        boolean valid = !cardNumber.isEmpty()
                && Validators.isValidFullName(fullName)
                && Validators.isValidOffice(officeText);

        if ("На дому".equals(type)) {
            valid = valid && !address.isEmpty();
        }

        Button okButton = (Button) getDialogPane().lookupButton(okButtonType);
        okButton.setDisable(!valid);
    }

    private Ticket createTicket() {
        try {
            String cardNumber = cardNumberField.getText().trim();
            String fullName = fullNameField.getText().trim();
            String office = officeField.getText().trim();
            int urgency = urgencyComboBox.getValue();
            String type = typeComboBox.getValue();

            if ("На дому".equals(type)) {
                String address = addressField.getText().trim();
                return new HomeVisitTicket(cardNumber, fullName, office, urgency, address);
            } else if ("Закрытый".equals(type)) {
                return new ClosedTicket(cardNumber, fullName, office, urgency, LocalDateTime.now());
            } else {
                return new Ticket(cardNumber, fullName, office, urgency);
            }
        } catch (NumberFormatException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}