package polyclinic.queue.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import polyclinic.queue.model.Ticket;

public class AddTicketDialog extends Dialog<Ticket> {

    private TextField cardNumberField;
    private TextField fullNameField;
    private TextField officeField;
    private ComboBox<Integer> urgencyComboBox;

    public AddTicketDialog() {
        setTitle("Добавление нового талона");
        setHeaderText("Введите данные пациента");

        ButtonType okButton = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
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
        urgencyComboBox.setValue(1);  // По умолчанию средняя срочность
        urgencyComboBox.setPromptText("Уровень срочности");

        grid.add(new Label("№ карты:"), 0, 0);
        grid.add(cardNumberField, 1, 0);
        grid.add(new Label("ФИО:"), 0, 1);
        grid.add(fullNameField, 1, 1);
        grid.add(new Label("Кабинет:"), 0, 2);
        grid.add(officeField, 1, 2);
        grid.add(new Label("Срочность:"), 0, 3);
        grid.add(urgencyComboBox, 1, 3);

        getDialogPane().setContent(grid);

        cardNumberField.requestFocus();

        setResultConverter(dialogButton -> {
            if (dialogButton == okButton) {
                try {
                    String cardNumber = cardNumberField.getText().trim();
                    String fullName = fullNameField.getText().trim();
                    int office = Integer.parseInt(officeField.getText().trim());
                    int urgency = urgencyComboBox.getValue();

                    return new Ticket(cardNumber, fullName, office, urgency);
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}