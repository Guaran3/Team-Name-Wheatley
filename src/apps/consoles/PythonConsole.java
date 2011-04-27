package apps.consoles;

import org.python.util.PythonInterpreter;

/**
 * PythonConsole is a Jython-based app that lets you interact with a Python
 * console that has full access to all of the Java classes in the project.
 * This allows you to quickly experiment with the classes, without having to
 * write a full-blown Java program.
 *
 * This uses the JythonConsole implementation of a console with all of the
 * nice features like history, tab completion, et cetera. JythonConsole is an
 * external open source project, accessible at:
 * 
 *    http://code.google.com/p/jythonconsole/
 *    
 * The license for JythonConsole is available in the licenses/ directory.
 * 
 * @author Sam
 */
public class PythonConsole {
    public static void main(String[] args) {
        PythonInterpreter interpreter = new PythonInterpreter();
        interpreter.exec("import scripts");        
        interpreter.exec("import external.JythonConsole.console");
        interpreter.exec("external.JythonConsole.console.main(locals())");
    }
}