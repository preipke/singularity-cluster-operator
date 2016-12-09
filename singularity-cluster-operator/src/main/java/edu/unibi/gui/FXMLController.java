package edu.unibi.gui;

import edu.unibi.cluster.ClusterController;
import edu.unibi.properties.PropertiesController;
import edu.unibi.main.Logger;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;

/**
 * Controller class for all GUI functionality and display.
 * Top level class within the application. Stores and interacts with 
 * PropertiesController and ClusterController.
 * @author Philo Reipke, University Bielefeld
 */
public class FXMLController implements Initializable
{
    private FileChooser fileChooser;
    
    private PropertiesController propertiesController;
    private ClusterController clusterController;
    
    // Properties Import
    @FXML private Button propertiesFileButton;
    @FXML private TextField propertiesFile;
    @FXML private CheckBox propertiesUseDefaultsCheckbox;
    
    // Text Log
    @FXML private TextArea textLog;
    
    // Authentification
    @FXML private TextField authEndpoint;
    
    @FXML private TextField authUsername;
    @FXML private PasswordField authPassword;
    @FXML private TextField authTenant;
    
    @FXML private TextField authKeyPairName;
    @FXML private TextField authKeyFile;
    @FXML private Button authKeyFileButton;
    
    // Cluster
    @FXML private ComboBox clusterMasterSize;
    @FXML private ComboBox clusterSlaveSize;
    @FXML private TextField clusterSlavesCount;
    
    @FXML private TextField singularityInputFile;
    @FXML private Button singularityInputFileButton;
    
    @FXML private Button clusterStartButton;
    @FXML private Button clusterStopButton;

    /**
     * Initializes the GUI state after constructing by the FXMLLoader.
     * @param location
     * @param resources 
     */
    @Override
    public void initialize(URL location , ResourceBundle resources) {
        
        Logger.initialize(textLog);
        
        fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        
        // Properties
        propertiesController = new PropertiesController(new File("openstack.properties"));
        File file = propertiesController.getCustomPropertiesFile();
        if (file != null) {
            propertiesFile.setText(file.getAbsolutePath());
            propertiesFile.setTooltip(new Tooltip(file.getAbsolutePath()));
        }
        
        // Cluster
        try {
            clusterController = new ClusterController(this , propertiesController);
            clusterMasterSize.setItems(clusterController.getFlavorChoices());
            clusterSlaveSize.setItems(clusterController.getFlavorChoices());
            file = new File("singularity.input");
            if (file.exists()) {
                clusterController.setInputFile(file);
                singularityInputFile.setText(file.getAbsolutePath());
                singularityInputFile.setTooltip(new Tooltip(file.getAbsolutePath()));
            }

            UpdateTextfieldEntries();
            Logger.log("Application initialized.");

            // Auto-run if all files avaiable
            if (UpdateAndEvaluateProperties()) {
                Logger.log("Auto-starting cluster...");
                StartCluster();
            }
        } catch (IOException ex) {
            Logger.log("Critical exception whilr initializing application!");
            Logger.log(ex.toString());
            Logger.log("Restart and try again.");
        }
    }
    
    // <editor-fold defaultstate="collapsed" desc=" GUI Functions : Buttons : Functions ">
    
    @FXML
    public void ImportPropertiesFile() {
        
        fileChooser.setTitle("Select properties file");
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            propertiesController.ImportProperties(file , propertiesUseDefaultsCheckbox.isSelected());
            propertiesFile.setText(file.getAbsolutePath());
            propertiesFile.setTooltip(new Tooltip(file.getAbsolutePath()));
            UpdateTextfieldEntries();
        }
    }
    
    @FXML
    public void SelectKeyFile() {
        
        fileChooser.setTitle("Select key file");
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            propertiesController.put("identity-file" , file.getAbsolutePath());
            authKeyFile.setText(propertiesController.get("identity-file"));
            VerifyTextIsFile(authKeyFile);
        }
    }
    
    @FXML
    public void SelectInputFile() {
        
        fileChooser.setTitle("Select input file");
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            clusterController.setInputFile(file);
            singularityInputFile.setText(file.getPath());
            VerifyTextIsFile(singularityInputFile);
        }
    }
    
    @FXML
    public void StartCluster() {
        
        if (!UpdateAndEvaluateProperties()) {
            Logger.log("Incorrect settings. Not initializing cluster.");
            return;
        }
        
        LockSettings();
        
        try {
            clusterController.StartCluster();
        } catch (IOException ex) {
            Logger.log("Exception starting the cluster.");
            Logger.log(ex.toString());
        }
    }
    
    @FXML
    public void StopCluster() {
        
        try {
            clusterController.StopCluster(false);
        } catch (IOException ex) {
            Logger.log("Exception starting the cluster.");
            Logger.log(ex.toString());
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc=" GUI Functions : Settings : Updating, Locking, Unlocking " > 
    
    /**
     * Updates the displayed text using the currently stored property values.
     */
    public void UpdateTextfieldEntries() {
        
        // Authentification
        authEndpoint.setText(propertiesController.get("openstack-endpoint"));
        
        authUsername.setText(propertiesController.get("openstack-username"));
        authPassword.setText(propertiesController.get("openstack-password"));
        authTenant.setText(propertiesController.get("openstack-tenantname"));
        
        authKeyPairName.setText(propertiesController.get("keypair"));
        authKeyFile.setText(propertiesController.get("identity-file"));
        
        // Cluster
        clusterMasterSize.setValue(propertiesController.get("master-instance-type"));
        clusterSlaveSize.setValue(propertiesController.get("slave-instance-type"));
        clusterSlavesCount.setText(propertiesController.get("slave-instance-count"));
        
        VerifyTextIsFile(authKeyFile);
        VerifyTextIsFile(singularityInputFile);
    }
    
    /**
     * Attempts to update all properties. The returned boolean value indicates
     * for some entries wether they are correct or not.
     * @return 
     */
    private boolean UpdateAndEvaluateProperties() {
        
        // Authentification
        propertiesController.put("openstack-endpoint" , authEndpoint.getText());
        
        propertiesController.put("openstack-username" , authUsername.getText());
        propertiesController.put("openstack-password" , authPassword.getText());
        propertiesController.put("openstack-tenantname" , authTenant.getText());
        
        propertiesController.put("keypair" , authKeyPairName.getText());
        propertiesController.put("identity-file" , authKeyFile.getText());
        
        propertiesController.put("master-instance-type" , clusterMasterSize.getValue().toString());
        propertiesController.put("slave-instance-type" , clusterSlaveSize.getValue().toString());
        propertiesController.put("slave-instance-count" , clusterSlavesCount.getText());
        
        if (!VerifyTextIsFile(authKeyFile) || !VerifyTextIsFile(singularityInputFile)) {
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * Locks all GUI elements related to authentification and cluster settings. 
     * Disables editing. Used to deny any changes in settings while processing.
     */
    private void LockSettings() {
        
        propertiesFileButton.setDisable(true);
        
        // Authentification settings
        authEndpoint.setEditable(false);
        
        authUsername.setEditable(false);
        authPassword.setEditable(false);
        authTenant.setEditable(false);
        
        authKeyPairName.setEditable(false);
        authKeyFileButton.setDisable(true);
        
        // Cluster settings
        clusterMasterSize.setDisable(true);
        clusterSlaveSize.setDisable(true);
        clusterSlavesCount.setEditable(false);
    
        singularityInputFileButton.setDisable(true);
    
        clusterStartButton.setDisable(true);
    }
    
    /**
     * Enables use of the button to stop the cluster.
     */
    public void UnlockStopButton() {
        clusterStopButton.setDisable(false);
    }
    
    /**
     * Unocks all GUI elements related to authentification and cluster settings. 
     * Enables editing. Should be invoked only when no process is running.
     */
    public void UnlockSettings() {
        
        propertiesFileButton.setDisable(false);
        
        // Authentification settings
        authEndpoint.setEditable(true);
        
        authUsername.setEditable(true);
        authPassword.setEditable(true);
        authTenant.setEditable(true);
        
        authKeyPairName.setEditable(true);
        authKeyFileButton.setDisable(false);
        
        // Cluster settings
        clusterMasterSize.setDisable(false);
        clusterSlaveSize.setDisable(false);
        clusterSlavesCount.setEditable(true);
    
        singularityInputFileButton.setDisable(false);
        
        clusterStartButton.setDisable(false);
        clusterStopButton.setDisable(true);
    }
    
    // </editor-fold>
    
    /**
     * Verifies that the text set within a TextField represents an existing File.
     * @param textfield
     * @return 
     */
    public Boolean VerifyTextIsFile(TextField textfield) {
        String path = textfield.getText();
        if (path != null) {
            File file = new File(path);
            if (!file.exists()) {
                textfield.setTooltip(new Tooltip("Specified file does not exist!"));
                textfield.setStyle("-fx-text-box-border: red");
                return false;
            }
            textfield.setStyle("-fx-text-box-border: lightgrey");
            textfield.setTooltip(new Tooltip(path));
        } else {
            textfield.setTooltip(new Tooltip("No file specified."));
        }
        return true;
    }
}
