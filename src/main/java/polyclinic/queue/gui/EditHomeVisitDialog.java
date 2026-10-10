package polyclinic.queue.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import polyclinic.queue.model.HomeVisitTicket;
import polyclinic.queue.util.Validators;

public class EditHomeVisitDialog extends Dialog<ButtonType> {

    private final TextField fullNameField;
    private final TextField officeField;
    private final ComboBox<Integer> urgencyComboBox;
    private final TextField addressField;
    private final ButtonType okButtonType;

    public EditHomeVisitDialog(HomeVisitTicket ticket) {
        setTitle("Редактирование талона на дому");
        setHeaderText("Измените данные вызова: " + ticket.getCardNumber());
        setResizable(true);
        getDialogPane().setMinSize(500, 350);
        getDialogPane().setPrefSize(500, 350);

        okButtonType = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        fullNameField = new TextField(ticket.getFullName());
        fullNameField.setPromptText("Иванов Иван Иванович");

        officeField = new TextField(String.valueOf(ticket.getOffice()));
        officeField.setPromptText("104 (или 104а или 104-а)");

        urgencyComboBox = new ComboBox<>();
        urgencyComboBox.getItems().addAll(0, 1, 2, 3);
        urgencyComboBox.setValue(ticket.getUrgency());
        addressField = new TextField(ticket.getAddress());

        grid.add(new Label("ФИО:"), 0, 0);
        grid.add(fullNameField, 1, 0);
        grid.add(new Label("Участок/Кабинет врача:"), 0, 1);
        grid.add(officeField, 1, 1);
        grid.add(new Label("Срочность:"), 0, 2);
        grid.add(urgencyComboBox, 1, 2);
        grid.add(new Label("Адрес:"), 0, 3);
        grid.add(addressField, 1, 3);

        getDialogPane().setContent(grid);
        fullNameField.requestFocus();

        fullNameField.textProperty().addListener((obs, old, newVal) -> validate());
        officeField.textProperty().addListener((obs, old, newVal) -> validate());
        addressField.textProperty().addListener((obs, old, newVal) -> validate());

        setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return ButtonType.OK;
            }
            return null;
        });

        validate();
    }

    private void validate() {
        String fullName = fullNameField.getText().trim();
        String officeText = officeField.getText().trim();
        String address = addressField.getText().trim();

        boolean valid = Validators.isValidFullName(fullName)
                && Validators.isValidOffice(officeText);

        Button okButton = (Button) getDialogPane().lookupButton(okButtonType);
        okButton.setDisable(!valid);
    }

    public String getFullName() {
        return fullNameField.getText().trim();
    }

    public String getOffice() {
        return officeField.getText().trim();
    }

    public int getUrgency() {
        return urgencyComboBox.getValue();
    }

    public String getAddress() {
        return addressField.getText().trim();
    }
}