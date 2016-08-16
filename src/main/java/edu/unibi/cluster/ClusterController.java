package edu.unibi.cluster;

import edu.unibi.properties.PropertiesController;
import edu.unibi.gui.FXMLController;
import edu.unibi.main.Logger;
import edu.unibi.main.Utilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Controls the cluster. Uses the external BiBiGrid.jar to create and destroy 
 * the cluster. Stores status information and interacts with the GUI.
 * @author Philo Reipke, Bielefeld University
 */
public class ClusterController
{
    private final FXMLController guiController;
    private final PropertiesController propertiesController;
    
    private final ProcessBuilder processBuilder;
    private final ProcessListener processListener;
    
    private final File fileBiBiGridJar;
    private final File fileSingularityScript;
    private File fileInput = null;
    
    private String user = null;
    private String clusterId = null;
    private String masterIp = null;
    
    private ObservableList<String> flavorChoices = null;
    
    /**
     * Constructor. Uses the given FXMLController to interact with the GUI, 
     * @param gui
     * @param properties
     * @throws java.io.IOException
     */
    public ClusterController(FXMLController gui, PropertiesController properties) throws IOException {
        
        guiController = gui;
        propertiesController = properties;
        
        // read available instance flavors from resource file
        InputStream inputFlavors = getClass().getClassLoader().getResourceAsStream("bibigrid/instance.flavors");
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(inputFlavors));
            ArrayList<String> flavorsListStrings = new ArrayList();
            String flavor;
            while ((flavor = br.readLine()) != null) {
                flavorsListStrings.add(flavor);
            }
            flavorChoices = FXCollections.observableArrayList(flavorsListStrings);
            
        } catch (FileNotFoundException ex) {
            Logger.log("Cluster: Resource 'instance.flavors' not found!");
            Logger.log(ex.toString());
        } catch (IOException ex) {
            Logger.log("Cluster: Exception reading resource 'instance.flavors'!");
            Logger.log(ex.toString());
        } finally {
            Utilities.close(br);
        }
        
        // create temporary files for cluster initialization
        try {
            fileBiBiGridJar = Utilities.CreateTempFile("BiBiGrid-1.0.jar" , "bibigrid/BiBiGrid-1.0.jar");
            fileSingularityScript = Utilities.CreateTempFile("init.sh" , "bibigrid/singularity.sh");
        } catch (IOException ex) {
            System.out.println("Cluster: Exception creating temporary executables.");
            System.out.println(ex.toString());
            throw ex;
        }
        
        processBuilder = new ProcessBuilder();
        processBuilder.redirectErrorStream(true);
        
        processListener = new ProcessListener(this);
        processListener.start();
    }
    
    /**
     * Attempts to start a cluster.
     * @throws java.io.IOException 
     */
    public void StartCluster() throws IOException {
        
        // Verify action
        synchronized (processListener) {
            
            // Current process still alive?
            if (processListener.isProcessAlive()) {
                Logger.log("Cluster: Still working. Please wait.");
                return;
            }

            // Already changing process?
            if (!processListener.lock()) {
                Logger.log("Cluster: Already working. Please wait.");
                return;
            }
        }
        
        user = propertiesController.get("openstack-username");
        if (user == null) {
            Logger.log("Cluster: No user specified. Aborting.");
            processListener.unlock();
            return;
        } else if (clusterId != null) {
            Logger.log("Cluster: Already or still running. Aborting.");
            processListener.unlock();
            return;
        }
        
        try {
            propertiesController.WritePropertiesFile();
        } catch (IOException ex) {
            Logger.log("Cluster: Exception writing properties file. Aborting.");
            Logger.log(ex.toString());
            processListener.unlock();
            return;
        }

        processBuilder.command(
                new String[]{ "java" ,
                              "-jar" , fileBiBiGridJar.getAbsolutePath() ,
                              "-o" , propertiesController.getTempPropertiesFile().getAbsolutePath() ,
                              "-u" , user ,
                              "-ex" , fileSingularityScript.getAbsolutePath() ,
                              "-c" });
        Process process = processBuilder.start();

        // Set process and unlock watcher
        synchronized (processListener) {
            
            processListener.setProcess(process);
            processListener.notify();
        }
    }
    
    /**
     * Attempts to stop the cluster.
     * @param force
     * @throws java.io.IOException 
     */
    public void StopCluster(boolean force) throws IOException {
        
        if (!force) {
            // Verify action
            synchronized (processListener) {

                // Current process still alive?
                if (processListener.isProcessAlive()) {
                    Logger.log("Cluster: Still working. Please wait.");
                    return;
                }

                // Already changing process?
                if (!processListener.lock()) {
                    Logger.log("Cluster: Already working. Please wait.");
                    return;
                }
            }

            if (clusterId == null) {
                Logger.log("Cluster: No cluster running.");
                processListener.unlock();
                return;
            }
        }

        processBuilder.command(
                new String[]{ "java" ,
                              "-jar" , fileBiBiGridJar.getAbsolutePath() ,
                              "-o" , propertiesController.getTempPropertiesFile().getAbsolutePath() ,
                              "-u" , user ,
                              "-t" , clusterId });
        Process process = processBuilder.start();

        if (!force) {
            // Set process and unlock watcher
            synchronized (processListener) {

                processListener.setProcess(process);
                processListener.notify();
            }
        }
    }
    
    /**
     * Interrupts the ProcessListener thread and terminates the cluster.
     */
    public void exit() {
        processListener.interrupt();
        try {
            StopCluster(true);
        } catch (IOException ex) {
            System.out.println("Exception forcing cluster shutdown.");
            System.out.println(ex.toString());
        }
    }
    
    /**
     * Unlocks the button to stop the cluster.
     */
    protected void UnlockStopButton() {
        Platform.runLater(() -> {
            guiController.UnlockStopButton();
        });
    }
    
    /**
     * Unlocks GUI elements for authentification and cluster settings.
     */
    protected void UnlockSettings() {
        Platform.runLater(() -> {
            guiController.UnlockSettings();
        });
    }
    
    /**
     * Sets the cluster's tasks input file.
     * @param file 
     */
    public void setInputFile(File file) {
        fileInput = file;
    }
    
    /**
     * Gets the cluster's tasks input file.
     * @return 
     */
    public File getInputFile() {
        return fileInput;
    }
    
    /**
     * Sets the current cluster ID.
     * @param id 
     */
    protected void setClusterId(String id) {
        clusterId = id;
    }
    
    /**
     * Gets the current cluster ID.
     * @return 
     */
    public String getClusterId() {
        return clusterId;
    }
    
    /**
     * Sets the current master IP.
     * @param ip 
     */
    protected void setMasterIp(String ip) {
        masterIp = ip;
    }
    
    /**
     * Gets the current master IP.
     * @return 
     */
    public String getMasterIp() {
        return masterIp;
    }

    /**
     * Gets the PropertiesController.
     * @return 
     */
    public PropertiesController getPropertiesController() {
        return propertiesController;
    }
    
    /**
     * Gets a list of available instance flavors.
     * @return 
     */
    public ObservableList<String> getFlavorChoices() {
        return flavorChoices;
    }
}
