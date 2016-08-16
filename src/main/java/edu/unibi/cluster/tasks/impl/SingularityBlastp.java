package edu.unibi.cluster.tasks.impl;

import edu.unibi.cluster.tasks.ITask;
import edu.unibi.main.Utilities;
import edu.unibi.properties.PropertiesController;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * ITask implementation for performing a protein BLAST.
 * @author Philo Reipke, Bielefeld University
 */
public class SingularityBlastp implements ITask
{
    private final PropertiesController propertiesController;
    
    private final String requestId = "BLASTP";
    
    private final String databaseSwiftContainer = "BLAST-DB";
    private final String[] databaseFileNames = new String[]{"swissprot.phr", "swissprot.pin", "swissprot.psq"};
    private final String databaseName = "swissprot";
    
    private String[] inputFileNames;
    private String[] outputFileNames;
    private File[] outputFiles;
    
    private int tasks = 0;
    private int queries = 0;

    public SingularityBlastp(PropertiesController properties) {
        propertiesController = properties;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }
    
    @Override
    public void MapInput(File sourceFile, File[] targetFiles) throws IOException {
        
        tasks = targetFiles.length;
        
        ArrayList<Integer> queryLineIndices = new ArrayList();
        ArrayList<String> queryLines = new ArrayList();
        String line;
        
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(sourceFile));
            
            int currentLine = 0;
            while ((line = br.readLine()) != null) {
                
                queryLines.add(line);
                if (line.contains(">contig-")) {
                    queryLineIndices.add(currentLine);
                }
                currentLine++;
            }
        } catch (Exception ex) {
            System.out.println("Exception reading input file.");
            System.out.println(ex.toString());
        } finally {
            Utilities.close(br);
        }
        
        queries = queryLineIndices.size();
        int queriesPerDeploy = (int) Math.ceil(queries / tasks);
        
        System.out.println("Queries in input file: " + queryLineIndices.size() + ".");
        System.out.println("Scheduling " + queriesPerDeploy + " per deploy.");
        
        // Create slave input files
        inputFileNames = new String[tasks];
        
        for (int i = 0; i < targetFiles.length; i++) {
            inputFileNames[i] = targetFiles[i].getName();
            
            // Write queries to file
            PrintWriter out = null;
            try {
                out = new PrintWriter(new FileOutputStream(targetFiles[i] , false));
                for (int j = 0; j < queriesPerDeploy; j++) {
                    
                    // Get number of lines in between current and next query
                    int from, to;
                    from = queryLineIndices.get(i * queriesPerDeploy + j);
                    if (queryLineIndices.size() > i * queriesPerDeploy + j + 1) {
                        to = queryLineIndices.get(i * queriesPerDeploy + j + 1);
                    } else {
                        to = queryLines.size();
                    }
                    
                    // Write query lines to file
                    for (int k = from; k < to; k++) {
                        out.println(queryLines.get(k));
                    }
                }
            } catch (Exception ex) {
                System.out.println("Exception writing queries to input files.");
                System.out.println(ex.toString());
            } finally {
                Utilities.close(out);
            }
        }
    }

    /**
     * Sets the files that will be used to store output data.
     * @param files 
     */
    @Override
    public void setOutputFiles(File[] files) {
        outputFiles = files;
    }
    
    /**
     * Writes commands to be executed on the cluster. Each slave receives 
     * a single String that represents a pipeline of commands to be performed.
     * @param objectContainer object storage container to be used for all tasks
     * @return set of commands
     */
    @Override
    public String[] WriteTaskCommands(String objectContainer) {
        
        String[] commands = new String[tasks];
        outputFileNames = new String[tasks];
        
        String endpoint, user, password, tenant;
        endpoint = propertiesController.get("openstack-endpoint");
        user = propertiesController.get("openstack-username");
        password = propertiesController.get("openstack-password");
        tenant = propertiesController.get("openstack-tenantname");
        
        for (int i = 0; i < commands.length; i++) {
            outputFileNames[i] = outputFiles[i].getName();
            
                            // set env
            commands[i] =     "export OS_AUTH_URL=" + endpoint + " ; "
                            + "export OS_USERNAME=" + user + " ; "
                            + "export OS_PASSWORD=" + password + " ; "
                            + "export OS_TENANT_NAME=" + tenant + " ; ";
            
                            // download database
            for (int j = 0; j < databaseFileNames.length; j++) {
                commands[i] += "swift download"
                               + " " + databaseSwiftContainer
                               + " " + databaseFileNames[j]
                               + " -o " + databaseFileNames[j] + " ; ";
            }
                            // download input
            commands[i] +=    "swift download " + objectContainer + " " + inputFileNames[i] + " ; "
                            // execute blastp
                            + "blastp -outfmt 6 -db " + databaseName
                                + " -query " + inputFileNames[i]
                                + " -out " + outputFileNames[i] + " ; "
                            // upload output
                            + "swift upload " + objectContainer + " " + outputFileNames[i]
                                + "  --object-name " + outputFileNames[i];
        }
        return commands;
    }
    
    /**
     * Reduces the output files produced by all tasks.
     * Produces a single file that contains the combined results of all slaves.
     * @param targetFile
     * @param sourceFiles
     * @throws IOException 
     */
    @Override
    public void ReduceOutput(File targetFile , File[] sourceFiles) throws IOException {
        
        PrintWriter writer = new PrintWriter(targetFile);
        BufferedReader br;
        String line;
        int read;
        
        for (File file : sourceFiles) {
            
            br = new BufferedReader(new FileReader(file));
            read = 0;
            
            while ((line = br.readLine()) != null) {
                
                writer.println(line);
                
                if (read % 50 == 0) {
                    writer.flush();
                }
            }
            writer.flush();
            
            br.close();
        }
        writer.close();
    }
}
