package se.su.inlupp;

import javafx.application.Application;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.File;
import java.util.Optional;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.util.Pair;
import java.util.List;
import java.util.ArrayList;

class PlaceNode extends StackPane {
  private final String name;
  private final Circle circle;
  private final Label label;
  private boolean selected = false;

  public PlaceNode(String name, double x, double y) {
    this.name = name;
    this.circle = new Circle(8, Color.LIGHTBLUE);
    this.circle.setStroke(Color.BLACK);
    Tooltip.install(this, new Tooltip(name));
    getChildren().addAll(circle);
    setLayoutX(x - circle.getRadius());
    setLayoutY(y - circle.getRadius());
    setOnMouseClicked(this::handleClick);
  }

  private void handleClick(MouseEvent event) {
    toggleSelected();
    event.consume();
  }

  public void toggleSelected() {
    if (!selected && Gui.selectedNodes.size() >= 2) return;
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

    // Knapprad
    // ToolBar toolBar = new ToolBar(
    // new Button("Find Path"),
    // new Button("Show Connection"),
    // new Button("New Place"),
    // new Button("New Connection"),
    // new Button("Change Connection"));

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
    newPlaceBtn.setOnAction(e -> {
      newPlaceBtn.setDisable(true); 
      activateNewPlaceMode(() -> newPlaceBtn.setDisable(false));
      });
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
  private void activateNewPlaceMode(Runnable onFinished) {
    mapPane.setCursor(Cursor.CROSSHAIR);
    mapPane.setOnMouseClicked(evt -> {
      double x = evt.getX();
      double y = evt.getY();
      TextInputDialog dialog = new TextInputDialog();
      dialog.setTitle("New Place");
      dialog.setHeaderText(null);
      dialog.setContentText("Ange namn på plats:");
      Optional<String> result = dialog.showAndWait();
      result.ifPresent(name -> {
        addVisualNode(name.trim(), x, y);
        markUnsavedChanges();
      });
      mapPane.setCursor(Cursor.DEFAULT);
      mapPane.setOnMouseClicked(null);
      onFinished.run();
    });
  }

  private void addVisualNode(String name, double x, double y) {
    PlaceNode node = new PlaceNode(name, x, y);
    mapPane.getChildren().add(node);
    graph.add(name);
  }

  public static void onPlaceNodeSelected(PlaceNode node) {
    if (node.isSelected()) {
      selectedNodes.add(node);
      if (selectedNodes.size() > 2) {
        PlaceNode old = selectedNodes.remove(0);
        old.toggleSelected();
      }
    } else {
      selectedNodes.remove(node);
    }
  }

  // connection
  private void handleNewConnection() {
    if (selectedNodes.size() != 2) {
      showAlert("Fel", "Välj exakt två platser för att skapa en förbindelse.");
      return;
    }
    PlaceNode a = selectedNodes.get(0), b = selectedNodes.get(1);
    if (graph.getEdgeBetween(a.getName(), b.getName()) != null) {
      showAlert("Fel", "En förbindelse finns redan.");
      return;
    }
    Dialog<Pair<String, Integer>> dialog = new Dialog<>();
    dialog.setTitle("New Connection");
    GridPane grid = new GridPane();
    TextField nameField = new TextField(), timeField = new TextField();
    grid.add(new Label("Name:"), 0, 0);
    grid.add(nameField, 1, 0);
    grid.add(new Label("Time:"), 0, 1);
    grid.add(timeField, 1, 1);
    dialog.getDialogPane().setContent(grid);
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    dialog.setResultConverter(btn -> {
      if (btn == ButtonType.OK) {
        try {
          return new Pair<>(nameField.getText(), Integer.parseInt(weightField.getText()));
        } catch (Exception ex) {
          return null;
        }
      }
      return null;
    });
    dialog.showAndWait().ifPresent(p -> {
      graph.connect(a.getName(), b.getName(), p.getKey(), p.getValue());
      drawEdge(a, b);
      markUnsavedChanges();
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
      showAlert("Fel", "Välj exakt två platser för att visa förbindelse.");
      return;
    }
    PlaceNode a = selectedNodes.get(0), b = selectedNodes.get(1);
    Edge<String> edge = graph.getEdgeBetween(a.getName(), b.getName());
    if (edge == null)
      showAlert("Fel", "Ingen förbindelse finns.");
    else
      showAlert("Connection", edge.getName() + ", vikt=" + edge.getWeight());
  }

  // change connection
  private void handleChangeConnection() {
    if (selectedNodes.size() != 2) {
      showAlert("Fel", "Välj exakt två platser för att ändra förbindelse.");
      return;
    }
    PlaceNode a = selectedNodes.get(0), b = selectedNodes.get(1);
    Edge<String> edge = graph.getEdgeBetween(a.getName(), b.getName());
    if (edge == null) {
      showAlert("Fel", "Ingen förbindelse finns.");
      return;
    }
    TextInputDialog dialog = new TextInputDialog(String.valueOf(edge.getWeight()));
    dialog.setTitle("Change Connection");
    dialog.setHeaderText(null);
    dialog.setContentText("Ny vikt:");
    dialog.showAndWait().ifPresent(val -> {
      try {
        int newW = Integer.parseInt(val);
        graph.setConnectionWeight(a.getName(), b.getName(), newW);
        showAlert("Success", "Vikten uppdaterad till " + newW);
        markUnsavedChanges();
      } catch (NumberFormatException ex) {
        showAlert("Fel", "Ogiltigt viktvärde.");
      }
    });
  }

  // Find path
  private void handleFindPath() {
    if (selectedNodes.size() != 2) {
      showAlert("Fel", "Välj exakt två platser för att hitta väg.");
      return;
    }
    PlaceNode a = selectedNodes.get(0), b = selectedNodes.get(1);
    if (!graph.pathExists(a.getName(), b.getName())) {
      showAlert("Fel", "Ingen väg finns.");
      return;
    }
    List<Edge<String>> path = graph.getPath(a.getName(), b.getName());
    StringBuilder sb = new StringBuilder();
    int total = 0;
    for (Edge<String> e : path) {
      sb.append("Till ").append(e.getDestination()).append(" via ")
          .append(e.getName()).append(" vikt ").append(e.getWeight()).append("\n");
      total += e.getWeight();
    }
    sb.append("Total vikt: ").append(total);
    showAlert("Path", sb.toString());
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
          while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty())
              continue;

            if (line.startsWith("Node:")) {
              // Format: Node: namn;x;y
              String nodeData = line.substring(5).trim();
              String[] parts = nodeData.split(";");
              if (parts.length != 3) {
                throw new Exception("Felaktigt nodformat: " + line);
              }

              String nodeName = parts[0];
              double x = Double.parseDouble(parts[1]);
              double y = Double.parseDouble(parts[2]);

              // Lägg till nod i grafen
              graph.add(nodeName);

              // TODO: Skapa visuell representation av noden
              // addVisualNode(nodeName, x, y);

            } else if (line.startsWith("Edge:")) {
              // Format: Edge: från;till;namn;vikt
              String edgeData = line.substring(5).trim();
              String[] parts = edgeData.split(";");
              if (parts.length != 4) {
                throw new Exception("Felaktigt edge-format: " + line);
              }

              String from = parts[0];
              String to = parts[1];
              String name = parts[2];
              int weight = Integer.parseInt(parts[3]);

              // Lägg till edge i grafen
              graph.connect(from, to, name, weight);

              // TODO: Skapa visuell representation av edge
              // addVisualEdge(from, to, name, weight);
            }
          }
        }

        hasUnsavedChanges = false;
        System.out.println("Graf-fil öppnad: " + file.getName());

      } catch (Exception ex) {
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
          boolean firstNode = true;
          for (String nodeName : graph.getNodes()) {
            if (!firstNode) {
              nodesLine.append(";");
            }

            // TODO: Hämta faktiska koordinater från visuella noder
            // För nu används dummy-koordinater
            double x = 0.0; // Ska hämtas från PlaceNode
            double y = 0.0; // Ska hämtas från PlaceNode

            nodesLine.append(nodeName).append(";").append(x).append(";").append(y);
            firstNode = false;
          }
          writer.println(nodesLine.toString());

          // 3. Resterande rader: alla förbindelser (en per rad)
          for (String fromNode : graph.getNodes()) {
            for (Edge<String> edge : graph.getEdgesFrom(fromNode)) {
              String toNode = edge.getDestination();
              String edgeName = edge.getName();
              int weight = edge.getWeight();

              writer.println(fromNode + ";" + toNode + ";" + edgeName + ";" + weight);
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
