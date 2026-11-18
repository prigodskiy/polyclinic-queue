package ru.polyclinic.queue.ui;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import ru.polyclinic.queue.model.Ticket;

import java.util.Set;

public class HeapVisualizer extends Pane {
    private static final int MAX_DEPTH = 4;

    private Ticket[] lastSnapshot;
    private Set<Integer> lastVisited;

    public HeapVisualizer() {
        widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        heightProperty().addListener((obs, oldVal, newVal) -> redraw());
    }

    public void draw(Ticket[] snapshot, Set<Integer> visitedIndices) {
        this.lastSnapshot = snapshot;
        this.lastVisited = visitedIndices;
        redraw();
    }

    private void redraw() {
        getChildren().clear();
        if (lastSnapshot == null || lastSnapshot.length == 0) return;

        int maxNodes = (1 << (MAX_DEPTH + 1)) - 1;
        int nodesToDraw = Math.min(lastSnapshot.length, maxNodes);

        for (int i = 0; i < nodesToDraw; i++) {
            int left = 2 * i + 1;
            int right = 2 * i + 2;
            if (left < nodesToDraw) drawLine(i, left);
            if (right < nodesToDraw) drawLine(i, right);
        }

        for (int i = 0; i < nodesToDraw; i++) {
            drawNode(i, lastSnapshot[i], lastVisited.contains(i));
        }

        Text text = new Text();
        if (lastSnapshot.length > nodesToDraw) {
            text.setText("... и еще " + (lastSnapshot.length - nodesToDraw) + " элементов (Всего в куче: " + lastSnapshot.length + ")");
        } else {
            text.setText("Всего в куче: " + lastSnapshot.length);
        }
        text.setX(20);
        text.setY(getHeight() - 20);
        text.setStyle("-fx-font-size: 14px; -fx-fill: gray;");
        getChildren().add(text);
    }

    private void drawLine(int parentIndex, int childIndex) {
        int pDepth = getDepth(parentIndex);
        int cDepth = getDepth(childIndex);
        Line line = new Line(
                getX(parentIndex, pDepth), getY(pDepth),
                getX(childIndex, cDepth), getY(cDepth)
        );
        line.setStroke(Color.LIGHTGRAY);
        getChildren().add(line);
    }

    private void drawNode(int index, Ticket ticket, boolean visited) {
        int depth = getDepth(index);
        double x = getX(index, depth);
        double y = getY(depth);

        double radius = Math.min(25, Math.max(12, getWidth() / (Math.pow(2, depth + 1) * 1.5)));

        Circle circle = new Circle(x, y, radius);
        circle.setFill(visited ? Color.ORANGE : Color.LIGHTBLUE);
        circle.setStroke(Color.BLACK);

        Text urgencyText = new Text(String.valueOf(ticket.getUrgency()));
        urgencyText.setX(x - (radius * 0.3));
        urgencyText.setY(y + (radius * 0.4));
        urgencyText.setStyle("-fx-font-size: " + (radius * 0.9) + "px; -fx-font-weight: bold;");

        Text cardText = new Text(ticket.getCardNumber());
        cardText.setX(x - (ticket.getCardNumber().length() * 2.5));
        cardText.setY(y + radius + 12);
        cardText.setStyle("-fx-font-size: 10px; -fx-fill: #333333;");

        getChildren().addAll(circle, urgencyText, cardText);
    }

    private int getDepth(int index) {
        return (int) (Math.log(index + 1) / Math.log(2));
    }

    private double getX(int index, int depth) {
        double width = getWidth() > 0 ? getWidth() : 1000;
        double sectionWidth = width / Math.pow(2, depth);
        int positionInLevel = index - ((1 << depth) - 1);
        return sectionWidth * positionInLevel + (sectionWidth / 2);
    }

    private double getY(int depth) {
        double height = getHeight() > 0 ? getHeight() : 600;
        double startY = 50;
        double spacing = (height - startY - 60) / MAX_DEPTH;
        return startY + depth * spacing;
    }
}