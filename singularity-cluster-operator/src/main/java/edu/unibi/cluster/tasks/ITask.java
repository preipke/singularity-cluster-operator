package edu.unibi.cluster.tasks;

import java.io.File;
import java.io.IOException;

/**
 * Interface for implementing Singularity tasks.
 * @author Philo Reipke, University Bielefeld
 */
public interface ITask
{
    /**
     * Gets the identifier for the implemented task. Will be the prefix used
     * as requestId for Singularity Requests.
     * @return 
     */
    public String getRequestId();
    
    /**
     * Supposed to map the given source file to the given target files. The
     * number of target files equals the number of available slaves, so that
     * each can receive a single input file.
     * @param sourceFile
     * @param targetFiles
     * @throws IOException 
     */
    public void MapInput(File sourceFile, File[] targetFiles) throws IOException;
    
    /**
     * Writes commands to be executed on the cluster. Each slave is supposed
     * to receive a single String that represents a pipeline of commands to
     * be performed.
     * @param objectContainer object storage container to be used for all tasks
     * @return set of commands
     */
    public String[] WriteTaskCommands(String objectContainer);
    
    /**
     * Sets the files that will be used to store output data.
     * @param files 
     */
    public void setOutputFiles(File[] files);
    
    /**
     * Reduces the output files produced by all tasks on the seperate slaves.
     * Supposed to produce a single file that contains the combined results
     * of all slaves.
     * @param targetFile
     * @param sourceFiles
     * @throws IOException 
     */
    public void ReduceOutput(File targetFile, File[] sourceFiles) throws IOException;
}
