package se.su.inlupp;

import javafx.application.Application;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.File;
import java.util.Optional;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.util.Pair;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

class PlaceNode extends StackPane {
  private final String name;
  private final Circle circle;
  private final Label label;
  private boolean selected = false;

  public PlaceNode(String name, double x, double y) {
    this.name = name;
    this.circle = new Circle(8, Color.LIGHTBLUE);
    this.circle.setStroke(Color.BLACK);
    this.label = new Label(name);
    getChildren().addAll(circle, label);
    setLayoutX(x - circle.getRadius());
    setLayoutY(y - circle.getRadius());
    setOnMouseClicked(this::handleClick);
  }

  private void handleClick(MouseEvent event) {
    toggleSelected();
    event.consume();
  }

  public void toggleSelected() {
    selected = !selected;
    circle.setFill(selected ? Color.RED : Color.LIGHTBLUE);
    Gui.onPlaceNodeSelected(this);
  }

  public boolean isSelected() {
    return selected;
  }

  public String getName() {
    return name;
  }

  public double getCenterX() {
    return getLayoutX() + circle.getRadius();
  }

  public double getCenterY() {
    return getLayoutY() + circle.getRadius();
  }

}

public class Gui extends Application {
  private Graph<String> graph = new ListGraph<>();
  private ImageView mapView;
  private Pane mapPane;
  private Stage primaryStage;
  private boolean hasUnsavedChanges = false;
  private String currentMapImagePath = null;
  private static List<PlaceNode> selectedNodes = new ArrayList<>();
  private Map<String,PlaceNode> placeNodeMap = new HashMap<>();

  @Override
  public void start(Stage stage) {
    this.primaryStage = stage;
    stage.setTitle("Karta med graf");

    // File-meny
    MenuBar menuBar = new MenuBar();
    Menu fileMenu = new Menu("File");
    MenuItem newMapItem = new MenuItem("New Map");
    MenuItem openItem = new MenuItem("Open");
    MenuItem saveItem = new MenuItem("Save");
    MenuItem saveImageItem = new MenuItem("Save Image");
    MenuItem exitItem = new MenuItem("Exit");

    fileMenu.getItems().addAll(newMapItem, openItem, saveItem, saveImageItem, new SeparatorMenuItem(), exitItem);
    menuBar.getMenus().add(fileMenu);

    Button findPathBtn = new Button("Find Path");
    Button showConnBtn = new Button("Show Connection");
    Button newPlaceBtn = new Button("New Place");
    Button newConnBtn = new Button("New Connection");
    Button changeConnBtn = new Button("Change Connection");
    ToolBar toolBar = new ToolBar(findPathBtn, showConnBtn, newPlaceBtn, newConnBtn, changeConnBtn);

    // Kartområde
    mapView = new ImageView();
    mapPane = new Pane(mapView);
    mapPane.setPrefSize(800, 600);

    // Layout
    BorderPane root = new BorderPane();
    root.setTop(new VBox(menuBar, toolBar));
    root.setCenter(mapPane);

    Scene scene = new Scene(root, 900, 700);
    stage.setScene(scene);
    stage.show();

    // Event handlers
    exitItem.setOnAction(e -> handleExit());
    newMapItem.setOnAction(e -> handleNewMapItem());
    openItem.setOnAction(e -> handleOpenItem());
    saveItem.setOnAction(e -> handleSaveItem());
    saveImageItem.setOnAction(e -> handleSaveImageItem());

    // verktygsknappar handlers
    newPlaceBtn.setOnAction(e -> activateNewPlaceMode());
    newConnBtn.setOnAction(e -> handleNewConnection());
    showConnBtn.setOnAction(e -> handleShowConnection());
    changeConnBtn.setOnAction(e -> handleChangeConnection());
    findPathBtn.setOnAction(e -> handleFindPath());

    // Hantera fönsterstängning
    stage.setOnCloseRequest(e -> {
      e.consume();
      handleExit();
    });
  }

  // new place
  private void activateNewPlaceMode() {
    mapPane.setCursor(Cursor.CROSSHAIR);

    mapPane.setOnMouseClicked(evt -> {
      double x = evt.getX();
      double y = evt.getY();
      TextInputDialog dialog = new TextInputDialog();
      dialog.setTitle("New Place");
      dialog.setHeaderText(null);
      dialog.setContentText("Name of place:");
      Optional<String> result = dialog.showAndWait();
      result.ifPresent(name -> {
        if (name.trim().isEmpty()) {
          showAlert("Error", "Name cannot be empty.");
          return;
        }
        addVisualNode(name.trim(), x, y);
        markUnsavedChanges();
      });
      mapPane.setCursor(Cursor.DEFAULT);
      mapPane.setOnMouseClicked(null);
    });
  }

  private void addVisualNode(String name, double x, double y) {
    PlaceNode node = new PlaceNode(name, x, y);
    mapPane.getChildren().add(node);
    graph.add(name);
    placeNodeMap.put(name, node);
  }

  public static void onPlaceNodeSelected(PlaceNode node) {
    if (node.isSelected()) {
      selectedNodes.add(node);
      if (selectedNodes.size() > 2) {
        PlaceNode last = selectedNodes.removeLast();
        last.toggleSelected();
      }
    } else {
      selectedNodes.remove(node);
    }
  }

  // connection
  private void handleNewConnection() {
    if (selectedNodes.size() != 2) {
      showAlert("Error!", "Two places must be selected to create a new connection.");
      return;
    }
    PlaceNode a = selectedNodes.get(0), b = selectedNodes.get(1);
    if (graph.getEdgeBetween(a.getName(), b.getName()) != null) {
      showAlert("Error!", "A connection already exists between these places.");
      return;
    }
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setTitle("New Connection");
    dialog.setHeaderText("Connection from " + a.getName() + " to " + b.getName());
    GridPane grid = new GridPane();
    TextField nameField = new TextField(), weightField = new TextField();
    grid.add(new Label("Name:"), 0, 0);
    grid.add(nameField, 1, 0);
    grid.add(new Label("Time:"), 0, 1);
    grid.add(weightField, 1, 1);

    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20, 150, 10, 10));

    dialog.getDialogPane().setContent(grid);
    ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

    dialog.showAndWait().ifPresent(action -> {
      if (action == okButtonType) {
        try {
          String name = nameField.getText().trim();
          String timeText = weightField.getText().trim();

          if (name.isEmpty()) {
            showAlert("Error", "Name cannot be empty.");
            return;
          }

          if (timeText.isEmpty()) {
            showAlert("Error", "Time cannot be empty.");
            return;
          }

          int time = Integer.parseInt(timeText);
          graph.connect(a.getName(), b.getName(), name, time);
          drawEdge(a, b);
          showAlert("Success", "Connection created successfully.");
          markUnsavedChanges();
        } catch (NumberFormatException ex) {
          showAlert("Error", "Invalid time value.");
        }
      }
    });
  }

  private void drawEdge(PlaceNode a, PlaceNode b) {
    Line line = new Line(a.getCenterX(), a.getCenterY(), b.getCenterX(), b.getCenterY());
    line.setStrokeWidth(2);
    mapPane.getChildren().add(line);
  }

  // Show connection
  private void handleShowConnection() {
    if (selectedNodes.size() != 2) {
      showAlert("Error", "Two places must be selected to show a connection.");
      return;
    }

    PlaceNode a = selectedNodes.get(0), b = selectedNodes.get(1);
    Edge<String> edge = graph.getEdgeBetween(a.getName(), b.getName());

    if (edge == null) {
      showAlert("Error", "No connection exists between " + a.getName() + " and " + b.getName());
    } else {
      Dialog<String> dialog = new Dialog<>();
      dialog.setTitle("Connection");
      dialog.setHeaderText("Connection from " + a.getName() + " to " + b.getName());
      GridPane grid = new GridPane();
      grid.setHgap(10);
      grid.setVgap(10);
      grid.setPadding(new Insets(20, 150, 10, 10));

      Label nameLabel = new Label("Name:");
      TextField nameField = new TextField(edge.getName());
      nameField.setEditable(false);

      Label timeLabel = new Label("Time:");
      TextField timeField = new TextField(String.valueOf(edge.getWeight()));
      timeField.setEditable(false);

      grid.add(nameLabel, 0, 0);
      grid.add(nameField, 1, 0);
      grid.add(timeLabel, 0, 1);
      grid.add(timeField, 1, 1);

      dialog.getDialogPane().setContent(grid);

      ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
      ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
      dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

      dialog.showAndWait();
    }

  }

  // change connection
  private void handleChangeConnection() {
    if (selectedNodes.size() != 2) {
      showAlert("Error!", "Two places must be selected to change a connection.");
      return;
    }

    PlaceNode a = selectedNodes.get(0), b = selectedNodes.get(1);
    Edge<String> edge = graph.getEdgeBetween(a.getName(), b.getName());

    if (edge == null) {
      showAlert("Error!", "No connection exists between " + a.getName() + " and " + b.getName());
      return;
    } else {
      Dialog<ButtonType> dialog = new Dialog<>();
      dialog.setTitle("Change Connection");
      dialog.setHeaderText("Connection from " + a.getName() + " to " + b.getName());
      GridPane grid = new GridPane();
      grid.setHgap(10);
      grid.setVgap(10);
      grid.setPadding(new Insets(20, 150, 10, 10));

      Label nameLabel = new Label("Name:");
      TextField nameField = new TextField(edge.getName());
      nameField.setEditable(false);

      Label timeLabel = new Label("Time:");
      TextField timeField = new TextField(String.valueOf(edge.getWeight()));
      timeField.setEditable(true);

      grid.add(nameLabel, 0, 0);
      grid.add(nameField, 1, 0);
      grid.add(timeLabel, 0, 1);
      grid.add(timeField, 1, 1);

      dialog.getDialogPane().setContent(grid);

      ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
      ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
      dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

      dialog.showAndWait().ifPresent(action -> {
        if (action == okButtonType) {
          try {
            int newWeight = Integer.parseInt(timeField.getText());
            graph.setConnectionWeight(a.getName(), b.getName(), newWeight);
            showAlert("Success", "Connection time updated to " + newWeight);
            markUnsavedChanges();
          } catch (NumberFormatException ex) {
            showAlert("Error", "Invalid time value.");
          }
        }
      });
    }
  }

  // Find path
  private void handleFindPath() {
    if (selectedNodes.size() != 2) {
      showAlert("Error!", "Two places must be selected to find a path.");
      return;
    }
    PlaceNode a = selectedNodes.get(0), b = selectedNodes.get(1);
    if (!graph.pathExists(a.getName(), b.getName())) {
      showAlert("Error!", "No path exists between " + a.getName() + " and " + b.getName());
      return;
    }
    List<Edge<String>> path = graph.getPath(a.getName(), b.getName());

    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setTitle("Message");
    dialog.setHeaderText("The Path from " + a.getName() + " to " + b.getName() + ":");

    TextArea pathArea = new TextArea();
    pathArea.setEditable(false);
    pathArea.setPrefRowCount(8);
    pathArea.setPrefColumnCount(50);

    StringBuilder sb = new StringBuilder();
    int total = 0;
    for (Edge<String> e : path) {
      sb.append("to ").append(e.getDestination()).append(" by ")
          .append(e.getName()).append(" takes ").append(e.getWeight()).append("\n");
      total += e.getWeight();
    }
    sb.append("Total ").append(total);

    pathArea.setText(sb.toString());

    dialog.getDialogPane().setContent(pathArea);

    ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().add(okButtonType);

    dialog.showAndWait();
  }

  private void handleNewMapItem() {
    // Kontrollera osparade ändringar först
    if (!checkUnsavedChanges()) {
      return;
    }

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Välj en kartbild");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Bildfiler", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
    File file = fileChooser.showOpenDialog(primaryStage);

    if (file != null) {
      try {
        // Rensa gamla noder/graf
        graph = new ListGraph<>();

        // Ladda ny bild
        Image newImage = new Image(file.toURI().toString());

        // Kontrollera att bilden laddades korrekt
        if (newImage.isError()) {
          showAlert("Fel", "Kunde inte ladda bilden. Kontrollera att filen är en giltig bildfil.");
          return;
        }

        // Sätt bilden
        mapView.setImage(newImage);
        mapView.setPreserveRatio(true);

        // Anpassa mapPane
        mapPane.setMinSize(newImage.getWidth(), newImage.getHeight());
        mapPane.setPrefSize(newImage.getWidth(), newImage.getHeight());

        // Rensa kartan och lägg till bilden
        mapPane.getChildren().clear();
        mapPane.getChildren().add(mapView);

        // Anpassa fönsterstorlek
        double menuHeight = 60;
        double windowWidth = Math.max(newImage.getWidth(), 400);
        double windowHeight = newImage.getHeight() + menuHeight;
        primaryStage.setWidth(windowWidth);
        primaryStage.setHeight(windowHeight);

        // Spara sökväg och markera som sparat
        currentMapImagePath = file.getAbsolutePath();
        hasUnsavedChanges = false;

        System.out.println("Ny karta inläst: " + file.getName());

      } catch (Exception ex) {
        showAlert("Fel", "Ett fel uppstod när bilden skulle laddas: " + ex.getMessage());
        ex.printStackTrace();
      }
    }
  }

  private void handleOpenItem() {
    // Kontrollera osparade ändringar först
    if (!checkUnsavedChanges()) {
      return;
    }

    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Öppna graf-fil");
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Graf-filer", "*.graph"));
    File file = fileChooser.showOpenDialog(primaryStage);

    if (file != null) {
      try {
        // Rensa gamla noder/graf
        graph = new ListGraph<>();
        placeNodeMap.clear();
        mapPane.getChildren().clear();
        mapPane.getChildren().add(mapView);
        try (Scanner scanner = new Scanner(file, "UTF-8")) {
          
        }
        hasUnsavedChanges = false;
        System.out.println("Graf-fil öppnad: " + file.getName());
      } 
      catch (Exception ex) {
        showAlert("Fel", "Kunde inte öppna graf-filen: " + ex.getMessage());
        ex.printStackTrace();
      }
    }
  }
        try (java.util.Scanner scanner = new java.util.Scanner(file, "UTF-8")) {
          // Läs första raden - sökväg till bildfil
          if (!scanner.hasNextLine()) {
            throw new Exception("Tom fil eller fel format");
          }

          String imagePath = scanner.nextLine().trim();

          // Hantera både URI-format (file:namn.gif) och vanliga filnamn (namn.gif)
          String actualImagePath = imagePath;
          if (imagePath.startsWith("file:")) {
            actualImagePath = imagePath.substring(5); // Ta bort "file:" prefix
          }

          // Hitta bildfilen
          File imageFile = new File(actualImagePath);
          if (!imageFile.exists()) {
            // Prova relativ sökväg från graf-filens katalog
            imageFile = new File(file.getParent(), actualImagePath);
            if (!imageFile.exists()) {
              throw new Exception("Bildfilen hittades inte: " + actualImagePath);
            }
          }

          // Ladda bakgrundsbilden
          Image newImage = new Image(imageFile.toURI().toString());
          if (newImage.isError()) {
            throw new Exception("Kunde inte ladda bildfilen: " + imageFile.getName());
          }

          // Sätt bilden
          mapView.setImage(newImage);
          mapView.setPreserveRatio(true);

          // Anpassa mapPane
          mapPane.setMinSize(newImage.getWidth(), newImage.getHeight());
          mapPane.setPrefSize(newImage.getWidth(), newImage.getHeight());

          // Rensa kartan och lägg till bilden
          mapPane.getChildren().clear();
          mapPane.getChildren().add(mapView);

          // Anpassa fönsterstorlek
          double menuHeight = 60;
          double windowWidth = Math.max(newImage.getWidth(), 400);
          double windowHeight = newImage.getHeight() + menuHeight;
          primaryStage.setWidth(windowWidth);
          primaryStage.setHeight(windowHeight);

          currentMapImagePath = imageFile.getAbsolutePath();

          // Läs noder och edges
         if (!scanner.hasNextLine()) throw new Exception("Missing nodes line");
          String nodesLine = scanner.nextLine();
          String[] parts = nodesLine.split(";");
          for (int i = 0; i < parts.length; i += 3) {
            String name = parts[i];
            double x    = Double.parseDouble(parts[i+1]);
            double y    = Double.parseDouble(parts[i+2]);
            addVisualNode(name, x, y);   
          }
          
          // Läs ut kanter utan dubbletter
          Set<String> seen = new HashSet<>();
          while (scanner.hasNextLine()) {
            String[] p   = scanner.nextLine().split(";");
            String from  = p[0], to = p[1], conn = p[2];
            int time     = Integer.parseInt(p[3]);
            String key   = from + "→" + to, rev = to + "→" + from;
            if (!seen.contains(key) && !seen.contains(rev)) {
              graph.connect(from, to, conn, time);
              drawEdge(placeNodeMap.get(from), placeNodeMap.get(to));
              seen.add(key);
            }
          }
        }   
          
        hasUnsavedChanges = false;
        System.out.println("Graf-fil öppnad: " + file.getName());
  }
  catch (Exception ex) {
      showAlert("Fel", "Kunde inte öppna graf-filen: " + ex.getMessage());
      ex.printStackTrace();
    }
  }
}


  private void handleSaveItem() {
    // Kontrollera att det finns en karta att spara
    if (currentMapImagePath == null) {
      showAlert("Fel", "Ingen karta att spara. Ladda först en karta med 'New Map' eller 'Open'.");
      return;
    }

    FileChooser fileChooser = new FileChooser();
    fileChooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Graf-filer", "*.graph"));
    File file = fileChooser.showSaveDialog(primaryStage);

    if (file != null) {
      try {
        // Se till att filen har rätt ändelse
        String fileName = file.getAbsolutePath();
        if (!fileName.endsWith(".graph")) {
          file = new File(fileName + ".graph");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {
          // 1. Första raden: sökväg till bildfilen
          writer.println(currentMapImagePath);

          // 2. Andra raden: alla noder (semikolonseparerade)
         StringBuilder nodesLine = new StringBuilder();
          boolean first = true;
          for (Map.Entry<String,PlaceNode> e : placeNodeMap.entrySet()) {
            if (!first) nodesLine.append(";");
            String name = e.getKey();
            PlaceNode nd = e.getValue();
            double x = nd.getCenterX(), y = nd.getCenterY();
            nodesLine.append(name).append(";").append(x).append(";").append(y);
            first = false;
          }
          writer.println(nodesLine.toString());

          // 3. Resterande rader: alla förbindelser (en per rad)
          Set<String> seen = new HashSet<>();
          for (String from : graph.getNodes()) {
            for (Edge<String> edge : graph.getEdgesFrom(from)) {
              String to  = edge.getDestination();
              String key = from + "→" + to, rev = to + "→" + from;
              if (!seen.contains(key) && !seen.contains(rev)) {
                writer.println(from + ";" + to + ";" + edge.getName() + ";" + edge.getWeight());
                seen.add(key);
    }
  }
}

        hasUnsavedChanges = false;
        System.out.println("Graf sparad: " + file.getName());

      } catch (Exception ex) {
        showAlert("Fel", "Kunde inte spara graf-filen: " + ex.getMessage());
        ex.printStackTrace();
      }
    }
  }

  private void handleSaveImageItem() {
    try {
      // Ta en snapshot av mapPane (kartområdet)
      WritableImage writableImage = mapPane.snapshot(new SnapshotParameters(), null);

      // Konvertera till BufferedImage
      BufferedImage bufferedImage = SwingFXUtils.fromFXImage(writableImage, null);

      // Spara som PNG i projektmappen
      File outputFile = new File("capture.png");
      ImageIO.write(bufferedImage, "png", outputFile);

      System.out.println("Skärmbild sparad som: " + outputFile.getAbsolutePath());

    } catch (Exception ex) {
      showAlert("Fel", "Kunde inte spara skärmbilden: " + ex.getMessage());
      ex.printStackTrace();
    }
  }

  private void handleExit() {
    if (checkUnsavedChanges()) {
      primaryStage.close();
    }
  }

  private boolean checkUnsavedChanges() {
    if (hasUnsavedChanges) {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.setTitle("Warning!");
      alert.setHeaderText(null);
      alert.setContentText("Unsaved changes, continue anyway?");

      ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
      ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
      alert.getButtonTypes().setAll(okButton, cancelButton);

      Optional<ButtonType> result = alert.showAndWait();
      return result.isPresent() && result.get() == okButton;
    }
    return true;
  }

  private void showAlert(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
  }

  // Metoder för att markera osparade ändringar
  public void markUnsavedChanges() {
    hasUnsavedChanges = true;
  }

  public void markSaved() {
    hasUnsavedChanges = false;
  }

  public static void main(String[] args) {
    launch(args);
  }
}
