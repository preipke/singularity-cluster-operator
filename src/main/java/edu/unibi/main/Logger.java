package edu.unibi.main;

import javafx.scene.control.TextArea;

/**
 * Simple logging class.
 * @author Philo Reipke, University Bielefeld
 */
public class Logger
{
    private static boolean isInitialized = false;
    private static TextArea textLog;
    
    /**
     * Initializes the Logger by setting the target TextArea.
     * @param textArea 
     */
    public static void initialize(TextArea textArea) {
        if (textArea != null) {
            textLog = textArea;
            isInitialized = true;
        }
    }
    
    /**
     * Prints a message to the text log and the command line.
     * @param msg 
     */
    public static synchronized void log(String msg) {
        if (msg != null) {
            if (isInitialized) {
                textLog.appendText(msg + "\n");
            } 
            System.out.println(msg);
        }
    }
}
