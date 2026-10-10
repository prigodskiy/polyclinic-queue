package polyclinic.queue.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import polyclinic.queue.model.HomeVisitTicket;

public class EditHomeVisitDialog extends Dialog<Void> {

    private final TextField fullNameField;
    private final TextField officeField;
    private final ComboBox<Integer> urgencyComboBox;
    private final TextField addressField;

    public EditHomeVisitDialog(HomeVisitTicket ticket) {
        setTitle("Редактирование талона на дому");
        setHeaderText("Измените данные вызова: " + ticket.getCardNumber());
        setResizable(true);

        getDialogPane().setMinSize(500, 300);
        getDialogPane().setPrefSize(500, 300);

        ButtonType okButton = new ButtonType("ОК", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        fullNameField = new TextField(ticket.getFullName());
        officeField = new TextField(String.valueOf(ticket.getOffice()));
        urgencyComboBox = new ComboBox<>();
        urgencyComboBox.getItems().addAll(0, 1, 2, 3);
        urgencyComboBox.setValue(ticket.getUrgency());
        addressField = new TextField(ticket.getAddress());

        grid.add(new Label("ФИО:"), 0, 0);
        grid.add(fullNameField, 1, 0);
        grid.add(new Label("Кабинет:"), 0, 1);
        grid.add(officeField, 1, 1);
        grid.add(new Label("Срочность:"), 0, 2);
        grid.add(urgencyComboBox, 1, 2);
        grid.add(new Label("Адрес:"), 0, 3);
        grid.add(addressField, 1, 3);

        getDialogPane().setContent(grid);
        fullNameField.requestFocus();
    }

    public String getFullName() { return fullNameField.getText(); }

    public int getOffice() {
        try {
            return Integer.parseInt(officeField.getText());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public int getUrgency() { return urgencyComboBox.getValue(); }

    public String getAddress() { return addressField.getText(); }
}
