package polyclinic.queue.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import polyclinic.queue.model.Ticket;
import polyclinic.queue.model.HomeVisitTicket;
import polyclinic.queue.model.ClosedTicket;

import java.time.LocalDateTime;

public class AddTicketDialog extends Dialog<Ticket> {

    private TextField cardNumberField;
    private TextField fullNameField;
    private TextField officeField;
    private ComboBox<Integer> urgencyComboBox;
    private ComboBox<String> typeComboBox;
    private TextField addressField;
    private GridPane grid;

    public AddTicketDialog() {
        setTitle("Добавление нового талона");
        setHeaderText("Введите данные пациента");
        setResizable(true);

        getDialogPane().setMinSize(500, 350);
        getDialogPane().setPrefSize(500, 350);

        ButtonType okButton = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        cardNumberField = new TextField();
        cardNumberField.setPromptText("Номер карты");

        fullNameField = new TextField();
        fullNameField.setPromptText("ФИО пациента");

        officeField = new TextField();
        officeField.setPromptText("Номер кабинета");

        urgencyComboBox = new ComboBox<>();
        urgencyComboBox.getItems().addAll(0, 1, 2, 3);
        urgencyComboBox.setValue(1);

        typeComboBox = new ComboBox<>();
        typeComboBox.getItems().addAll("Обычный", "На дому", "Закрытый");
        typeComboBox.setValue("Обычный");
        typeComboBox.setOnAction(e -> onTypeChanged());

        addressField = new TextField();
        addressField.setPromptText("Адрес вызова");

        buildForm();

        getDialogPane().setContent(grid);

        cardNumberField.requestFocus();

        setResultConverter(dialogButton -> {
            if (dialogButton == okButton) {
                try {
                    String cardNumber = cardNumberField.getText().trim();
                    String fullName = fullNameField.getText().trim();
                    int office = Integer.parseInt(officeField.getText().trim());
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
                    showAlert("Ошибка", "Кабинет должен быть числом.");
                    return null;
                } catch (IllegalArgumentException e) {
                    showAlert("Ошибка", e.getMessage());
                    return null;
                }
            }
            return null;
        });
    }

    private void buildForm() {
        grid.getChildren().clear();
        grid.setMinWidth(400);
        grid.setMinHeight(250);

        grid.add(new Label("№ карты:"), 0, 0);
        grid.add(cardNumberField, 1, 0);
        grid.add(new Label("ФИО:"), 0, 1);
        grid.add(fullNameField, 1, 1);
        grid.add(new Label("Кабинет:"), 0, 2);
        grid.add(officeField, 1, 2);
        grid.add(new Label("Срочность:"), 0, 3);
        grid.add(urgencyComboBox, 1, 3);
        grid.add(new Label("Тип:"), 0, 4);
        grid.add(typeComboBox, 1, 4);

        if ("На дому".equals(typeComboBox.getValue())) {
            grid.add(new Label("Адрес:"), 0, 5);
            grid.add(addressField, 1, 5);
        }
    }

    private void onTypeChanged() {
        buildForm();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}