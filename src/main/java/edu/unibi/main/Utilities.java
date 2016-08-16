package edu.unibi.main;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides unspecific functionality used within the application.
 * @author Philo Reipke, University Bielefeld
 */
public class Utilities
{
    //public static Path tempDirectoryPath = null;
    private final static String filePrefix = "singularity-";
    
    /**
     * Creates a temporary file from an optionally given resource. 
     * File can only be read, written or executed by the creator. 
     * Automatically deletes the file on application exit.
     * @param fileName identifier to be contained within the temporary file name
     * @param resourcePath path within the internal resources (optional)
     * @return the created file
     * @throws java.io.IOException 
     */
    public static File CreateTempFile(String fileName, String resourcePath) throws IOException {
        
        /*if (tempDirectoryPath == null) {
            tempDirectoryPath = Files.createTempDirectory(null);
            System.getSecurityManager().checkDelete(tempDirectoryPath.toFile().toString());
            System.out.println("Temp path: " + tempDirectoryPath);
            System.out.println("Temp path: " + tempDirectoryPath.toString());
            System.out.println("Temp path: " + tempDirectoryPath.toFile().toString());
        }*/
        
        File tempFile = null;
        InputStream input = null;
        OutputStream output = null;
        
        try {
            // Create temporary file
            //tempFile = File.createTempFile(tempDirectoryPath.getFileName() + File.separator + fileName , null);
            //System.out.println("Temp file: " + tempFile);
            tempFile = File.createTempFile(filePrefix + fileName , null);
            tempFile.setReadable(false , false);
            tempFile.setReadable(true , true);
            tempFile.setWritable(true , true);
            tempFile.setExecutable(true , true);
            tempFile.deleteOnExit();
            
            // Read resource and write to temp file
            output = new FileOutputStream(tempFile);
            if (resourcePath != null) {
                input = Main.class.getClassLoader().getResourceAsStream(resourcePath);

                byte[] buffer = new byte[1024];
                int read;

                if (input == null) {
                    Logger.log("Resource '" + fileName + "' at '" + resourcePath + "' not available!");
                } else {
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer , 0 , read);
                    }
                }
            }
        } catch (IOException ex) {
            throw(ex);
        } finally {
            close(input);
            close(output);
        }
        return tempFile;
    }
    
    /**
     * Parses part from a given String.
     * Removes all characters from before the last index of the starting 
     * substring to the first index of the ending substring.
     * @param line the complete String
     * @param start the substring which last index is used to parse 
     * @param end the tailing substring which first index is used to parse 
     * @return the resulting substring, or NULL if it cannot be parsed
     */
    public static String parseSubstring(String line, String start, String end){
        
        if (line == null || start == null || end == null)
            return null;
        
        int s = line.indexOf(start) + start.length();
        int e = line.indexOf(end, s);
        
        if (s >= e)
            return null;
        
        return line.substring(s, e);
    }

    /**
     * Closes a given Closable.
     * @param stream 
     */
    public static void close(final Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (final IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
