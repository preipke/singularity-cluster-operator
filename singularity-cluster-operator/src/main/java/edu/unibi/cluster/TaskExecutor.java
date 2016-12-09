package edu.unibi.cluster;

import edu.unibi.cluster.tasks.ITask;
import edu.unibi.main.Logger;
import edu.unibi.main.Utilities;
import edu.unibi.properties.PropertiesController;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openstack4j.api.OSClient.OSClientV2;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.storage.object.SwiftContainer;
import org.openstack4j.model.storage.object.options.ContainerListOptions;
import org.openstack4j.model.storage.object.options.ObjectLocation;
import org.openstack4j.openstack.OSFactory;

/**
 * Executes and monitors singularity tasks.
 * Handles all functionality, such as mapping and uploading inputs, deploying
 * tasks to Singularity, downloading and reducing outputs, managing object 
 * storage containers.
 * @author Philo Reipke, University Bielefeld
 */
public class TaskExecutor
{
    private final ClusterController clusterController;
    private final PropertiesController propertiesController;
    
    private final String objectContainerNamePrefix = "SINGULARITY-";
    private final String objectContainerNameSuffix = "-" + LocalDate.now(ZoneId.of("Europe/Berlin"));
    private final String objectContainer;
    
    private final String singularityUrlPrefix = "http://";
    private final String singularityUrlSuffix = ":7099/singularity/api";
    private final String singularityUrlApi;
    
    private final String inputFileNamePrefix = "input-";
    private final String outputFileNamePrefix = "output-";
    
    private final String resourceCpu = "2";
    private final String resourceMemoryMb = "24000"; // limited within singularity
    
    private final String requestType = "RUN_ONCE";
    private final String requestSlavePlacement = "SEPARATE_BY_REQUEST";
    
    private OSClientV2 os;
    
    private ITask currentModule;
    
    /**
     * Constructor. Uses the ProcessListener to access information stored within
     * the application, such as properties, cluster IP and input file.
     * @param listener 
     */
    public TaskExecutor(ProcessListener listener) {
        
        clusterController = listener.getClusterController();
        propertiesController = clusterController.getPropertiesController();
        
        objectContainer = objectContainerNamePrefix
                          + propertiesController.get("openstack-username")
                          + objectContainerNamePrefix;
        
        singularityUrlApi = singularityUrlPrefix
                            + clusterController.getMasterIp()
                            + singularityUrlSuffix;
    }
    
    /**
     * Runs the given module.
     * @param module
     * @throws IOException 
     */
    public void RunModule(ITask module) throws IOException {
        
        File inputFile, inputFiles[], outputFiles[];
        String request, deploy, commands[];
        String requestId = module.getRequestId();
        
        // Get the number of slaves, equals the resulting number of tasks
        int tasks;
        try {
            tasks = Integer.parseInt(propertiesController.get("slave-instance-count"));
        } catch (NumberFormatException ex) {
            System.out.println("Number of slaves not specified in properties!");
            System.out.println(ex.toString());
            System.out.println("Aborting.");
            return;
        }
        
        log("Running module.");
        currentModule = module;
        
        // Preparing temp files
        inputFile = clusterController.getInputFile();
        inputFiles = new File[tasks];
        outputFiles = new File[tasks];
        for (int deployId = 1; deployId <= tasks; deployId++) {
            inputFiles[deployId-1] = Utilities.CreateTempFile(inputFileNamePrefix + deployId + "-" , null);
            outputFiles[deployId-1] = Utilities.CreateTempFile(outputFileNamePrefix + deployId + "-", null);
        }
        
        log("Mapping input.");
        module.setOutputFiles(outputFiles);
        module.MapInput(inputFile, inputFiles);
        
        log("Uploading input.");
        UploadInput(inputFiles);
        
        // Create and post request
        log("Posting requests and deploys.");
        commands = module.WriteTaskCommands(objectContainer);

        int statusCode;
        for (int taskId = 1; taskId <= commands.length; taskId++) {

            // Request
            request = "{\"id\": \"" + requestId + taskId + "\","
                      + "\"requestType\": \"" + requestType + "\","
                      //+ "\"slavePlacement\": \"" + requestSlavePlacement + "\","
                      + "\"instances\": 1 }";

            statusCode = PostJson(singularityUrlApi + "/requests" , request);

            /* Evaluating status code
            if (statusCode == 200) {
                log("Request (" + taskId + ") successfully created.");
            } else if (statusCode == 409) {
                log("Request (" + taskId + ") pending. ");
            } else if (statusCode == 400) {
                log("Request (" + taskId + ") failed. Request object is invalid.");
            } else {
                log("Request (" + taskId + ") failed. Unknown reason. Status code: " + statusCode + ".");
            }*/

            // Deploy
            deploy = "{\"deploy\":{"
                        + "\"requestId\":\"" + requestId + taskId + "\","
                        + "\"id\":\"1\","
                        + "\"resources\":{"
                            + "\"cpus\": " + resourceCpu + ","
                            + "\"memoryMb\": " + resourceMemoryMb + ","
                            + "\"numPorts\": 0"
                        + "},\"command\":\""
                        + commands[taskId-1] + "\"}}";

            statusCode = PostJson(singularityUrlApi + "/deploys" , deploy);

            /* Evaluating status code
            if (statusCode == 200) {
                log("Deploy (" + taskId + ") successfully scheduled.");
            } else if (statusCode == 409) {
                log("Deploy (" + taskId + ") pending. A current deploy is in progress.");
            } else if (statusCode == 400) {
                log("Deploy (" + taskId + ") failed. Deploy object is invalid.");
            } else {
                log("Deploy (" + taskId + ") failed. Unknown reason. Status code: " + statusCode + ".");
            }*/
        }
        
        // Wait for tasks to finish
        log("Waiting for tasks to finish. This may take a while...");
        long time = System.currentTimeMillis();
        do {
            synchronized (this) {
                try {
                    wait(10000); // wait 10 seconds before getting status again
                } catch (Exception ex) {
                    break;
                }
            }
        } while (GetActiveTasks() != 0);
        log("All tasks finished!");
        
        // Elapsed time
        time = System.currentTimeMillis() - time;
        Date date = new Date(time);
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        log("Elapsed time: " + formatter.format(date));
        
        // Download output
        log("Downloading output.");
        outputFiles = Download(outputFiles); 
        
        // Save results and clear object storage
        final File[] inputFilesCopy = inputFiles;
        final File[] outputFilesCopy = outputFiles;
        Platform.runLater(() -> {
            
            // Store results
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save results");
            File outputFile = fileChooser.showSaveDialog(null);
            if (outputFile == null) {
                outputFile = new File(inputFile.getName() + ".results");
            }
            try {
                module.ReduceOutput(outputFile , outputFilesCopy);
            } catch (IOException ex) {
                log("Exception writing results to output file.");
                log(ex.toString());
            }
            
            // Remove temporary files from object storage
            CleanObjectStorage(inputFilesCopy, outputFilesCopy);
        });
    }
    
    /**
     * Uploads files to the object storage. Creates the temporary container
     * if it is not existing already.
     * @param files 
     */
    private void UploadInput(File[] files) {
        
        if (files == null) {
            return;
        }
        
        os = OSFactory.builderV2()
                .endpoint(propertiesController.get("openstack-endpoint"))
                .credentials(propertiesController.get("openstack-username"), propertiesController.get("openstack-password"))
                .tenantName(propertiesController.get("openstack-tenantname"))
                .authenticate();
        
        // Find containers matching the current container name
        List<? extends SwiftContainer> containers = os.objectStorage()
                .containers()
                .list(ContainerListOptions.create()
                        .startsWith(objectContainer));
        
        // Create temporary container if not existing
        if (containers.isEmpty()) {
            os.objectStorage().containers().create(objectContainer);
        } 
        
        // Upload files to object storage
        for (File file : files) {
            os.objectStorage().objects().put(objectContainer, file.getName(), Payloads.create(file));
        }
    }
    
    /**
     * Downloads data from the object storage container.
     * Uses the given files names to target an object within the container.
     * Streams the containing data to the corresponding local file.
     * @param files
     * @return 
     */
    private File[] Download(File[] files) throws IOException {
        
        if (files == null) {
            return null;
        }
        
        for (File file : files) {
            os.objectStorage().objects().download(objectContainer , file.getName()).writeToFile(file);
        }
        
        return files;
    }
    
    /**
     * Posts a given JSON String to the given URL.
     * @param url
     * @param json
     * @return the response status code.
     * @throws MalformedURLException
     * @throws IOException 
     */
    private int PostJson(String url, String json) throws MalformedURLException , IOException {
        
        if (url == null || json == null) {
            return -1;
        }
        
        URL urlObj = new URL(url);
        HttpURLConnection con = (HttpURLConnection)urlObj.openConnection();
        
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type" , "application/json");
        con.setRequestProperty("Content-Length" , Integer.toString(json.getBytes().length));

        con.setDoInput(true);
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(json);
        wr.flush();
        wr.close();

        return con.getResponseCode();
    }
    
    /**
     * Gets the number of active tasks from the Singularity REST API.
     */
    private int GetActiveTasks() throws MalformedURLException , IOException {
        
        URL urlObj = new URL(singularityUrlApi + "/state");
        HttpURLConnection con = (HttpURLConnection)urlObj.openConnection();
        
        con.setRequestMethod("GET");
        
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory jf = new MappingJsonFactory();
        JsonParser jp;
        JsonNode elements;
            
        jp = jf.createJsonParser(new InputStreamReader(con.getInputStream()));
        elements = mapper.readTree(jp);
        jp.close();
        
        return elements.get("activeTasks").getIntValue();
    }
    
    /**
     * Cleans the object storage. Removes the given input and output files from 
     * the current object storage container and deletes the container.
     * @param input
     * @param output 
     */
    private void CleanObjectStorage(File[] input, File[] output) {
        
            os = OSFactory.builderV2()
                    .endpoint(propertiesController.get("openstack-endpoint"))
                    .credentials(propertiesController.get("openstack-username") , propertiesController.get("openstack-password"))
                    .tenantName(propertiesController.get("openstack-tenantname"))
                    .authenticate();
            
            for (File file : input) {
                os.objectStorage().objects().delete(ObjectLocation.create(objectContainer , file.getName()));
            }
            for (File file : output) {
                os.objectStorage().objects().delete(ObjectLocation.create(objectContainer , file.getName()));
            }
            os.objectStorage().containers().delete(objectContainer);
    }
    
    /**
     * Writes message using the Logger. Adds the current modules identifier. 
     * @param msg 
     */
    private void log(String msg) {
        Platform.runLater(() -> {
            Logger.log(currentModule.getRequestId() + ": " + msg);
        });
    }
}
