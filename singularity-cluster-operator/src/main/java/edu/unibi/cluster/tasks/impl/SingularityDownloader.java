package edu.unibi.cluster.tasks.impl;

import edu.unibi.cluster.tasks.ITask;
import edu.unibi.properties.PropertiesController;
import java.io.File;
import java.io.IOException;

/**
 * Used to download a database once every slave.
 * Deprecated as Mesos executer restricts access across folders of different
 * tasks. This task's actions have been included directy within SingularityBlastp.
 * @author PR
 */
@Deprecated
public class SingularityDownloader implements ITask
{
    private final PropertiesController propertiesController;
    
    private final String requestId = "DB-DOWNLOAD";
    
    private final String databaseSwiftContainer = "BLAST-DB";
    private final String[] databaseFileNames = new String[]{"swissprot.phr", "swissprot.pin", "swissprot.psq"};
    private final String databasePath = "/tmp/";

    public SingularityDownloader(PropertiesController properties) {
        propertiesController = properties;
    }
    
    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public String[] WriteTaskCommands(String objectContainer) {
        
        int slaves = 1;
        try {
            slaves = Integer.parseInt(propertiesController.get("slave-instance-count"));
        } catch (NumberFormatException ex) {
            System.out.println("Exception parsing slave count.");
            System.out.println(ex.toString());
        }
        
        String endpoint, user, password, tenant;
        endpoint = propertiesController.get("openstack-endpoint");
        user = propertiesController.get("openstack-username");
        password = propertiesController.get("openstack-password");
        tenant = propertiesController.get("openstack-tenantname");
        
        String[] deploys = new String[slaves];
        int deployId;
        
        for (int i = 0; i < deploys.length; i++) {
            deployId = i + 1;
            deploys[i] = "{ \"deploy\": {"
                         + "\"requestId\":\"" + requestId + "\","
                         + "\"id\":\"" + deployId + "\","
                         + "\"command\":\""
                         // Set environment variables
                         + "export OS_AUTH_URL=" + endpoint + " ; "
                         + "export OS_USERNAME=" + user + " ; "
                         + "export OS_PASSWORD=" + password + " ; "
                         + "export OS_TENANT_NAME=" + tenant + " ; "
                         // Change working directory
                         + "cd ; ";
            // Download database
            for (int j = 0; j < databaseFileNames.length; j++) {
                deploys[i] += "swift download "
                              + databaseSwiftContainer + " "
                              + databaseFileNames[j]
                              + " -o " + databasePath + databaseFileNames[j] + " ; ";
            }
            deploys[i] += "\" } }";
        }
        return deploys;
    }

    @Override
    public void MapInput(File sourceFile , File[] targetFiles) throws IOException {
    }

    @Override
    public void ReduceOutput(File targetFile , File[] sourceFiles) throws IOException {
    }

    @Override
    public void setOutputFiles(File[] files) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
