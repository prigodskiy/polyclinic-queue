package polyclinic.queue.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;
import polyclinic.queue.model.Ticket;

public class EditTicketDialog extends Dialog<Pair<String, Integer>> {

    private TextField fullNameField;
    private TextField officeField;
    private ComboBox<Integer> urgencyComboBox;

    public EditTicketDialog(Ticket ticket) {
        setTitle("Редактирование талона");
        setHeaderText("Измените данные талона: " + ticket.getCardNumber());

        ButtonType okButton = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        fullNameField = new TextField(ticket.getFullName());
        fullNameField.setPromptText("ФИО пациента");

        officeField = new TextField(String.valueOf(ticket.getOffice()));
        officeField.setPromptText("Номер кабинета");

        urgencyComboBox = new ComboBox<>();
        urgencyComboBox.getItems().addAll(0, 1, 2, 3);
        urgencyComboBox.setValue(ticket.getUrgency());
        urgencyComboBox.setPromptText("Уровень срочности");

        grid.add(new Label("ФИО:"), 0, 0);
        grid.add(fullNameField, 1, 0);
        grid.add(new Label("Кабинет:"), 0, 1);
        grid.add(officeField, 1, 1);
        grid.add(new Label("Срочность:"), 0, 2);
        grid.add(urgencyComboBox, 1, 2);

        getDialogPane().setContent(grid);

        okButton.getButtonData();
        getDialogPane().lookupButton(okButton).setDisable(false);

        setResultConverter(dialogButton -> {
            if (dialogButton == okButton) {
                try {
                    String fullName = fullNameField.getText();
                    int office = Integer.parseInt(officeField.getText());
                    int urgency = urgencyComboBox.getValue();
                    return new Pair<>(fullName, office);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        fullNameField.requestFocus();
    }

    public String getFullName() {
        return fullNameField.getText();
    }

    public int getOffice() {
        try {
            return Integer.parseInt(officeField.getText());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public int getUrgency() {
        return urgencyComboBox.getValue();
    }
}