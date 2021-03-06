package loop.controller;

//import com.sun.webkit.dom.DocumentImpl;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import loop.model.Group;
import loop.model.Segment;
import loop.model.plugin.Plugin;
import loop.model.plugin.PluginControl;
import loop.model.repository.CentralRepository;
import loop.model.repository.FileIO;
import loop.model.simulationengine.distributions.DiscreteDistribution;
import loop.model.simulationengine.distributions.DiscreteUniformDistribution;
import loop.view.controls.multislider.MultiSlider;
import loop.view.controls.multislider.Range;
import org.controlsfx.control.ListSelectionView;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This class represents the controller associated with the group creation window. It creates
 * new groups based on the user input and notifies the HeadController whenever a new
 * group has been created.
 *
 * @author Pierre Toussing
 */
public class GroupController implements CreationController<Group> {

    private static final String defaultDistributionName = DiscreteUniformDistribution.NAME;

    private Map<TabController, Tab> tabControllers = new HashMap<TabController, Tab>();

    private List<Consumer<Group>> elementCreatedHandlers = new ArrayList<Consumer<Group>>();

    /*------global properties-----*/
    @FXML
    private TextField groupNameTextField;

    @FXML
    private TextField descriptionTextField;

    @FXML
    private Menu loadMenu;

    @FXML
    private Button saveGroupButton;

    @FXML
    private Button resetGroupButton;

    /*------segment organisation-----*/

    @FXML
    private Button addSegmentButton;

    @FXML
    private CheckBox isCohesiveCheckBox;

    /*-----Tabs-----*/
    @FXML
    private TabPane segmentTabs;

    @FXML
    private VBox multiSliderBox;

    private Stage stage;
    private MultiSlider multiSlider;

    private List<DoubleProperty> sliderValues = new ArrayList<>();

    private int id = 0;

    @FXML
    void initialize() {
        segmentTabs.getTabs().addListener((ListChangeListener.Change<? extends Tab> c) -> {
            int i = 1;
            for (Tab tab : segmentTabs.getTabs()) {
                tab.setText("Segment " + (i++));
            }
        });
	    /*ChangeListener<Number> listener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
                segmentTabs.setPrefHeight(((VBox) segmentTabs.getSelectionModel().getSelectedItem().getContent()).getPrefHeight());
            }
	    };
	    segmentTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
	       if (oldTab == null) ((VBox) oldTab.getContent()).prefHeightProperty().removeListener(listener);
	       if (newTab == null) ((VBox) newTab.getContent()).prefHeightProperty().addListener(listener);
	    });*/
        isCohesiveCheckBox.setSelected(true);

        CentralRepository.getInstance().getGroupRepository().getAllEntityNames().forEach(grpName -> {
            MenuItem grpItem = new MenuItem(grpName);
            loadMenu.getItems().add(grpItem);
            grpItem.setOnAction(event -> setGroup(CentralRepository.getInstance().getGroupRepository().getEntityByName(grpName)));
        });

        addSegmentTab();

        multiSlider = new MultiSlider(0, 100, false);

        DoubleProperty sliderProp = new SimpleDoubleProperty();
        sliderProp.bindBidirectional(multiSlider.getRange(0).highProperty());
        sliderValues.add(sliderProp);

        multiSliderBox.getChildren().add(multiSlider);
    }

    /**
     * Set the stage of this population creation window. Must be called by the creating controller upon creation.
     *
     * @param stage the stage
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @Override
    public void registerElementCreated(Consumer<Group> action) {
        elementCreatedHandlers.add(action);
    }

    /*------------------------------button handlers------------------------------*/


    private void resetMultiSlider() {
        for (int i = 0; i < sliderValues.size() - 1; i++)
            multiSlider.removeLast();
        DoubleProperty prop = sliderValues.get(0);
        sliderValues.clear();
        prop.setValue(100);
        sliderValues.add(prop);
    }

    @FXML
    void resetGroup(ActionEvent event) {
        //confirm
        Alert alert = new Alert(AlertType.CONFIRMATION, "Are you sure you want to reset all settings?", ButtonType.YES, ButtonType.NO);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
        if (alert.getResult() == ButtonType.NO) {
            return;
        }

        //reset
        groupNameTextField.setText("");
        descriptionTextField.setText("");
        tabControllers.clear();
        segmentTabs.getTabs().clear();
        isCohesiveCheckBox.setSelected(true);
        id = 0;

        addSegmentTab();

        resetMultiSlider();
    }

    @FXML
    void exportGroup(ActionEvent event) {
        if (!validateSettings(true)) return;

        Group group = createGroup();

        //save dialog
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Group");
        fileChooser.setInitialDirectory(FileIO.GROUP_DIR);
        fileChooser.setInitialFileName(group.getName().toLowerCase().replace(' ', '_'));
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Loop Group File", "*.grp");
        fileChooser.getExtensionFilters().add(extFilter);
        File saveFile = fileChooser.showSaveDialog(stage);

        if (saveFile == null) {
            return;
        }

        try {
            FileIO.saveEntity(saveFile, group);
        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(AlertType.ERROR, "File could not be saved.", ButtonType.OK);
            alert.showAndWait();
            return;
        }
    }

    @FXML
    void saveGroup() {
        if (!validateSettings(true)) return;

        Group group = createGroup();

        //TODO unsch�n, aber wie sonst?
        if (CentralRepository.getInstance().getGroupRepository().containsEntityName(groupNameTextField.getText())) {
            Alert alert = new Alert(AlertType.CONFIRMATION, "A group with this name already exists. Do you want to overwrite it? Note that"
                    + " in that case all populations currently containing the overwritten group would from now on contain this one instead.",
                    ButtonType.YES, ButtonType.NO);
            alert.showAndWait();
            boolean override = (alert.getResult() == ButtonType.YES);
            if (!override) return;

            //overwrite group in repository and populations
            String groupName = groupNameTextField.getText();
            CentralRepository.getInstance().getGroupRepository().removeEntity(groupName);
        }

        this.elementCreatedHandlers.forEach(handler -> handler.accept(group));
        stage.close();
    }

    private boolean validateSettings(boolean alertIfFaulty) {
        if (groupNameTextField.getText().trim().equals("") || descriptionTextField.getText().trim().equals("")) {
            if (alertIfFaulty) {
                Alert alert = new Alert(AlertType.ERROR, "Name and description must not be empty.", ButtonType.OK);
                alert.showAndWait();
            }
            return false;
        }
        if (tabControllers.keySet().stream().map(c -> c.returnSegment()).anyMatch(seg -> seg.getStrategyNames().isEmpty())) {
            if (alertIfFaulty) {
                Alert alert = new Alert(AlertType.ERROR, "All segments must have at least one chosen strategy.", ButtonType.OK);
                alert.showAndWait();
            }
            return false;
        }
        return true;
    }

    @FXML
    void importGroup(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Group");
        fileChooser.setInitialDirectory(FileIO.GROUP_DIR);
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("Loop Group File", "*.grp");
        fileChooser.getExtensionFilters().add(extFilter);
        File file = fileChooser.showOpenDialog(stage);

        if (file == null) {
            return;
        }

        Group group;
        try {
            group = (Group) FileIO.loadEntity(file);
        } catch (IOException e) {
            Alert alert = new Alert(AlertType.ERROR, "File could not be opened.", ButtonType.OK);
            alert.showAndWait();
            e.printStackTrace();
            return;
        }

        List<String> unknownStrategies = new ArrayList<String>();
        List<String> unknownCapitalDistributionNames = new ArrayList<String>();
        group.getSegments().forEach(seg -> {
            seg.getStrategyNames().forEach(stratName -> {
                if (!CentralRepository.getInstance().getStrategyRepository().containsEntityName(stratName))
                    if (!unknownStrategies.contains(stratName)) unknownStrategies.add(stratName);
            });
            String distName = seg.getCapitalDistributionName();
            if (!CentralRepository.getInstance().getDiscreteDistributionRepository().containsEntityName(distName)) {
                if (!unknownCapitalDistributionNames.contains(distName)) {
                    unknownCapitalDistributionNames.add(distName);
                }
            }
        });

        if (unknownStrategies.isEmpty() && unknownCapitalDistributionNames.isEmpty()) {
            setGroup(group);
            return;
        }

        String errorMsg = "";
        if (!unknownStrategies.isEmpty()) {
            errorMsg += "The selected group contains the following unknown strategies:";
            for (String strat : unknownStrategies) errorMsg += "\n - " + strat;
            errorMsg += "\n";
        }

        if (!unknownCapitalDistributionNames.isEmpty()) {
            errorMsg += "\nThe selected group contains the following unknown capital distributions:";
            for (String dist : unknownCapitalDistributionNames) errorMsg += "\n - " + dist;
        }

        Alert alert = new Alert(AlertType.ERROR, errorMsg, ButtonType.OK);
        alert.showAndWait();
    }

    private void setGroup(Group group) {
        groupNameTextField.setText(group.getName());
        descriptionTextField.setText(group.getDescription());
        isCohesiveCheckBox.setSelected(group.isCohesive());
        id = 0;

        tabControllers.clear();
        segmentTabs.getTabs().clear();

        // Initialize the first tab
        // required for proper work of multislider
        Segment segment = group.getSegments().get(0);
        TabController controller = addSegmentTab();
        controller.distributionChoice.getSelectionModel().select(segment.getCapitalDistributionName());
        ((PluginControl) controller.distributionPluginPane.getChildren().get(0)).setParameters(segment.getCapitalDistributionParameters());
        segment.getStrategyNames().forEach(s -> {
            controller.strategyChoice.getSourceItems().remove(s);
            controller.strategyChoice.getTargetItems().add(s);
        });

        resetMultiSlider();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            int i = 1;

            @Override
            public void run() {
                if (i < group.getSegmentCount()) {
                    int j = i;
                    Platform.runLater(() -> {
                        TabController tabController = addSegmentTab();
                        Segment segment = group.getSegments().get(j);

                        tabController.distributionChoice.getSelectionModel().select(segment.getCapitalDistributionName());
                        ((PluginControl) tabController.distributionPluginPane.getChildren().get(0)).setParameters(segment.getCapitalDistributionParameters());
                        segment.getStrategyNames().forEach(s -> {
                            tabController.strategyChoice.getSourceItems().remove(s);
                            tabController.strategyChoice.getTargetItems().add(s);
                        });

                    });
                } else if (i < 2 * group.getSegmentCount() - 1) {
                    int j = i - group.getSegmentCount() + 1;
                    Platform.runLater(() -> {
                        double val = group.getSegmentSizes().get(j - 1) * 100d;
                        if (j > 1) val += group.getSegmentSizes().get(j - 2) * 100d;
                        sliderValues.get(j - 1).setValue(val);
                    });
                } else this.cancel();
                i++;
            }
        }, 0, 10);
        segmentTabs.getSelectionModel().select(0);
    }

    @FXML
    void handleAddSegment(ActionEvent event) {
        addSegmentTab();
    }

    /*---------------------------------private helper methods---------------------------------*/

    private TabController addSegmentTab() {
        TabController tabController = new TabController(this, id++);
        Tab newTab = new Tab();
        newTab.setOnCloseRequest((e) -> {
            if (tabController.index == 0) {
                e.consume(); // 1. Segment not removable
                return;
            }
            tabController.handleClosed(null);
        });
        Node content = tabController.getContent();
        content.getStyleClass().add(String.format("tab-area%d", tabController.index % 4));
        newTab.setContent(content);
        newTab.getStyleClass().add(String.format("tab%d", tabController.index % 4));
        tabControllers.put(tabController, newTab);
        segmentTabs.getTabs().add(newTab);
        segmentTabs.getSelectionModel().select(newTab);

        // Update MultiSlider
        if (sliderValues.size() > 0) {
            DoubleProperty lastProp = sliderValues.get(sliderValues.size() - 1);
            if (sliderValues.size() == 1) lastProp.setValue(50);
            else {
                DoubleProperty lastLastProp = sliderValues.get(sliderValues.size() - 2);
                lastProp.setValue(lastLastProp.getValue() + (lastProp.getValue() - lastLastProp.getValue()) / 2);
            }
            multiSlider.addRange(lastProp.get(), 100d,
                    (i) -> segmentTabs.getSelectionModel().select(i));
            Range sliderRange = multiSlider.getRange(sliderValues.size());

            sliderRange.lowProperty().bindBidirectional(lastProp);
            DoubleProperty sliderProp = new SimpleDoubleProperty();
            /*lastProp.addListener((c, n, o) -> {
                Double newVal = sliderProp.getValue() + ((double) o - (double) n);
                if (newVal > 100) newVal = 100d;
                sliderProp.setValue(newVal);
            });*/
            sliderProp.bindBidirectional(sliderRange.highProperty());
            sliderValues.add(sliderProp);
        }
        return tabController;
    }

    private void removeTab(TabController controller) {
        int size = sliderValues.size();
        List<Double> values = sliderValues.stream().map(DoubleProperty::getValue).collect(Collectors.toList());
        int index = controller.getIndex();
        double value = sliderValues.get(index).get() - sliderValues.get(index - 1).get();

        for (int i = index; i < sliderValues.size() - 1; i++) {
            double x = sliderValues.get(i - 1).get();
            double x_a = sliderValues.get(i).get();
            double x_b = sliderValues.get(i + 1).get();
            double c = x + (x_b - x_a);
            sliderValues.get(i).setValue(c);
        }
        sliderValues.remove(sliderValues.size() - 1);
        multiSlider.removeLast();
        for (TabController tabController : tabControllers.keySet()) {
            if (tabController.getIndex() > index) {
                int lastIndex = tabController.index;
                tabController.index--;
                tabController.getContent().getStyleClass().removeIf(str -> str.contains(String.format("tab-area%d", lastIndex % 4)));
                tabController.getContent().getStyleClass().add(String.format("tab-area%d", tabController.index % 4));
                tabControllers.get(tabController).getStyleClass()
                        .removeIf(str -> str.contains(String.format("tab%d", lastIndex % 4)));
                tabControllers.get(tabController).getStyleClass()
                        .add(String.format("tab%d", tabController.index % 4));
            }
        }
        sliderValues.get(sliderValues.size() - 1).setValue(100d);
        id--;

        segmentTabs.getTabs().remove(tabControllers.get(controller));
        tabControllers.remove(controller);
    }

    private Group createGroup() {
        List<Segment> segments = tabControllers.keySet().stream().map(c -> c.returnSegment()).collect(Collectors.toList());
        List<Double> segmentSizes = new ArrayList<Double>();
        double lastVal = 0;
        for (DoubleProperty sliderValue : sliderValues) {
            segmentSizes.add((sliderValue.getValue() - lastVal) / 100d);
            lastVal = sliderValue.getValue();
        }
        Group group = new Group(groupNameTextField.getText(), descriptionTextField.getText(), segments, segmentSizes, isCohesiveCheckBox.isSelected());
        return group;
    }

    private class TabController {

        private static final String FXML_NAME = "/view/controls/segmentTab.fxml";

        @FXML
        private ChoiceBox<String> distributionChoice;

        @FXML
        private HBox distributionPluginPane;

        @FXML
        private ListSelectionView<String> strategyChoice;

        private GroupController parent;

        @FXML
        private Node content;
        private int index = 0;

        public TabController(GroupController parent, int index) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_NAME));
            loader.setController(this);
            try {
                loader.load();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.parent = parent;
            this.index = index;
        }

        @FXML
        void initialize() {
            //setup strategy choice
            strategyChoice.setCellFactory(view -> new TooltipCell());
            for (String s : CentralRepository.getInstance().getStrategyRepository().getAllEntityNames()) {
                strategyChoice.getSourceItems().add(s);
            }

            //setup distribution choice
            distributionChoice.getItems().addAll(CentralRepository.getInstance()
                    .getDiscreteDistributionRepository().getAllEntityNames());
            distributionChoice.getSelectionModel().select(defaultDistributionName);
            distributionChoice.setTooltip(createTooltip(CentralRepository.getInstance()
                    .getDiscreteDistributionRepository().getEntityByName(defaultDistributionName).getDescription()));
            distributionChoice.valueProperty().addListener((obs, oldDistName, newDistName) -> {
                Plugin<DiscreteDistribution> plugin = CentralRepository.getInstance()
                        .getDiscreteDistributionRepository().getEntityByName(newDistName);
                distributionPluginPane.getChildren().clear();
                distributionPluginPane.getChildren().add(plugin.getRenderer().renderPlugin());
                distributionChoice.setTooltip(createTooltip(plugin.getDescription()));
            });

            PluginControl p = CentralRepository.getInstance().getDiscreteDistributionRepository()
                    .getEntityByName(defaultDistributionName).getRenderer().renderPlugin();
            distributionPluginPane.getChildren().add(p);
        }

        private class TooltipCell extends ListCell<String> {

            @Override
            protected void updateItem(String strategy, boolean empty) {
                super.updateItem(strategy, empty);

                if (!empty) {
                    setText(strategy);
                    Tooltip.install(this,
                            createTooltip(CentralRepository.getInstance().getStrategyRepository().getEntityByName(strategy).getDescription()));
                }
            }

            private Tooltip createTooltip(String desc) {
                Tooltip tooltip = new Tooltip(desc);
                tooltip.getStyleClass().add("ttip");
                tooltip.setWrapText(true);
                tooltip.setPrefWidth(600);
                return tooltip;
            }
        }

        @FXML
        void handleClosed(ActionEvent event) {
            parent.removeTab(this);
        }

        /**
         * Creates a Segment out of the user data.
         *
         * @return Segment created out of the user data
         */
        public Segment returnSegment() {
            String capitalDistributionName = distributionChoice.getValue();
            List<Double> capitalDistributionParameters = ((PluginControl) distributionPluginPane.getChildren().get(0)).getParameters();
            List<String> strategyNames = new ArrayList<String>(strategyChoice.getTargetItems());
            return new Segment(capitalDistributionName, capitalDistributionParameters, strategyNames);
        }

        public int getIndex() {
            return index;
        }

        public Node getContent() {
            return content;
        }

        private Tooltip createTooltip(String desc) {
            Tooltip tooltip = new Tooltip(desc);
            tooltip.getStyleClass().add("ttip");
            tooltip.setWrapText(true);
            tooltip.setPrefWidth(600);
            return tooltip;
        }
    }
}
