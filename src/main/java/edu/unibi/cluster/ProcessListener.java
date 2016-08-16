package edu.unibi.cluster;

import edu.unibi.cluster.tasks.impl.SingularityBlastp;
import edu.unibi.cluster.tasks.impl.SingularityDownloader;
import edu.unibi.main.Logger;
import edu.unibi.main.Utilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import javafx.application.Platform;

/**
 * Listens and evaluates the output of the currently stored BiBiGrid process. 
 * Retrieves values such as cluster ID and master IP. Controls initialization
 * of the TaskExecutor.
 * @author Philo Reipke, Bielefeld University
 * @since 0.1
 */
public class ProcessListener extends Thread
{
    private final ClusterController clusterController;
    
    private Process process = null;
    
    private Boolean isLocked = false;
    
    /**
     * Constructor. Uses the given ClusterController to update the GUI.
     * @param controller
     */
    public ProcessListener(ClusterController controller) {
        clusterController = controller;
    }

    /**
     * Gets the ClusterController.
     * @return 
     */
    public ClusterController getClusterController() {
        return clusterController;
    }
    
    /**
     * Listens to the set process' streams and evaluates the output on 
     * notification. All output is written using the Logger and evaluated to 
     * retrieve the current process' state and data such as cluster ID and 
     * master IP. Runs tasks using the TaskPerformer on successful cluster 
     * initialization.
     */
    @Override
    public void run() {
        
        while (true) {
            
            // Wait for notification
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException ex) {
                    System.out.println(ex);
                }
            }
            
            boolean clusterInitialized = false;

            // Read stream and process output
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            try {
                String msg;
                String masterIpIdent = "export BIBIGRID_MASTER=";
                
                while ((msg = br.readLine()) != null) {
                    
                    final String msgFinal = msg;
                    log("BiBiGrid: " + msgFinal);
                    
                    // Evaluate BiBiGrid output
                    if (msg.contains(") successfully created!") && msg.contains("Cluster (ID: ")) { // success, cluster ID
                        
                        msg = Utilities.parseSubstring(msg , "Cluster (ID: " , ") successfully created!");
                        log("Cluster ID: " + msg);
                        clusterController.setClusterId(msg);
                        
                    } else if (msg.contains("export BIBIGRID_MASTER=")) { // success, master IP
                        
                        msg = msg.substring(msg.lastIndexOf(masterIpIdent) + masterIpIdent.length());
                        log("Master IP: " + msg);
                        clusterController.setMasterIp(msg);
                        clusterInitialized = true;
                        clusterController.UnlockStopButton();
                        
                    } else if (msg.contains("Aborting operation. No instances started/terminated.")) { // fail
                        
                        if (clusterController.getClusterId() == null) {
                            clusterController.UnlockSettings();
                        }
                        
                    } else if ((msg.contains(") successfully terminated") && msg.contains("Cluster (ID: ")) || // terminated
                            (msg.contains("No suitable bibigrid cluster with ID: [") && msg.contains("] found."))) { // not found
                        
                        clusterController.setClusterId(null);
                        clusterController.setMasterIp(null);
                        clusterController.UnlockSettings();
                    }
                }
            } catch (IOException ex) {
                log("Exception listening to process stream.");
                log(ex.toString());
            } finally {
                Utilities.close(br);
            }
            
            // Unlock watcher
            synchronized (this) {
                unlock();
            }
            
            // Execute tasks if cluster has been initialized
            if (clusterInitialized) {
                log("Initializing tasks...");
                TaskExecutor taskPerformer = new TaskExecutor(this);
                try {
                    //taskPerformer.RunModule(new SingularityDownloader(clusterController.getPropertiesController()));
                    taskPerformer.RunModule(new SingularityBlastp(clusterController.getPropertiesController()));
                } catch (Exception ex) {
                    log("Exception performing tasks.");
                    log(ex.toString());
                }
            }
        }
    }
    
    /**
     * Sets the stored process.
     * @param pr 
     */
    protected void setProcess(Process pr) {
        process = pr;
    }
    
    /**
     * Gets the stored process.
     * @return 
     */
    public Process getProcess() {
        return process;
    }
    
    /**
     * Checks if the stored process is still alive.
     * @return Indicates wether the stored process is still alive or not.
     */
    public boolean isProcessAlive() {
        if (process != null) {
            return process.isAlive();
        } else {
            return false;
        }
    }

    /**
     * Attempts to lock this ProcessWatcher.
     * @return Indicates wether locking has been successful or not.
     */
    public boolean lock() {
        if (isLocked) {
            return false;
        } else {
            return isLocked = true;
        }
    }

    /**
     * Unlocks this ProcessWatcher.
     */
    public void unlock() {
        isLocked = false;
    }
    
    /**
     * Writes a message using the Logger.
     * @param msg 
     */
    private void log(String msg) {
        Platform.runLater(() -> {
            Logger.log(msg);
        });
    }
}
