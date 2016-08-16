package edu.unibi.properties;

import edu.unibi.main.Logger;
import edu.unibi.main.Utilities;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Stores relevant properties inside a key-value collection.
 * Can also be used to import properties from and write them to files.
 * @author Philo Reipke, University Bielefeld
 */
public final class PropertiesController
{
    private Properties properties = null;
    private File customPropertiesFile;
    private File tempPropertiesFile;
    
    public PropertiesController(File file) {
        customPropertiesFile = file;
        
        if (file.exists()) {
            ImportProperties(customPropertiesFile , false);
        } else {
            customPropertiesFile = null;
            ImportProperties(customPropertiesFile , false);
        }
    }
    
    /**
     * Imports properties from file.
     * @param file custom properties file
     * @param keepDefaults can be set to use some default values
     */
    public void ImportProperties(final File file, final boolean keepDefaults) {
        
        Properties defaultProperties = new Properties();
        Properties customProperties = new Properties();
        
        try {
            defaultProperties.load(getClass().getClassLoader().getResourceAsStream("bibigrid/default.properties"));
        } catch (IOException ex) {
            Logger.log("Properties: Exception importing default values.");
            Logger.log("Properties: " + ex.toString());
        }
        
        if (file != null) {
            if (file.exists()) {
                
                customPropertiesFile = file;
                
                Logger.log("Properties: Importing custom values.");
                Logger.log("Properties: " + file.toString());
                try {
                    InputStream input = new FileInputStream(file);
                    customProperties.load(input);
                    if (keepDefaults) {
                        Logger.log("Properties: Keeping default property values for existing custom values.");
                        for (Entry<Object , Object> property : defaultProperties.entrySet()) {
                            if (customProperties.containsKey(property.getKey())) {
                                customProperties.remove(property.getKey());
                            }
                        }
                    } else {
                        Logger.log("Properties: Overwriting default property values by existing custom values.");
                    }
                } catch (IOException ex) {
                    Logger.log("Properties: Exception importing custom values!");
                    Logger.log("Properties: " + ex.toString());
                }
            }
        } 
        properties = new Properties();
        properties.putAll(defaultProperties);
        properties.putAll(customProperties);
    }
    
    /**
     * Writes all stored properties to a temporary file.
     * @return written file
     * @throws IOException 
     */
    public File WritePropertiesFile() throws IOException {
        
        if (tempPropertiesFile == null) {
            tempPropertiesFile = Utilities.CreateTempFile("openstack.properties" , null);
        }
        
        PrintWriter writer = new PrintWriter(tempPropertiesFile);

        properties.forEach((key , value) -> {
            if (key.equals("identity-file")) {
                value = value.toString().replace("\\", "/");
            }
            writer.println(key + "=" + value);
            writer.flush();
        });
        
        Utilities.close(writer);
        
        return tempPropertiesFile;
    }
    
    /**
     * Stores a key-value pair in the Properties structure.
     * @param key
     * @param value 
     */
    public void put(Object key, Object value) {
        if (key != null) {
            if (value != null) {
                properties.put(key , value);
            } else {
                properties.remove(key);
            }
        }
    }
    
    /**
     * Gets the value for a given key within the Properties structure.
     * @param key
     * @return the corresponding value.
     */
    public String get(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Gets the temporary properties file.
     * @return 
     */
    public File getTempPropertiesFile() {
        return tempPropertiesFile;
    }

    /**
     * Gets the currently used custom properties file.
     * @return 
     */
    public File getCustomPropertiesFile() {
        return customPropertiesFile;
    }
}
