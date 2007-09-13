package ibis.ipl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Utility methods for setting and getting configuration properties. The
 * {@link #getConfigurationProperties()} method obtains the properties in the
 * following order: first, some hardcoded properties are set. Next, a file
 * <code>ibis.properties</code> is obtained from the classpath, if present. If
 * so, it is read as a properties file, and the properties contained in it are
 * set, possibly overriding already set properties. Next, the same is done for
 * the property file <code>ibis.properties</code> in the user home directory,
 * if present. And finally, the same is done for the property file
 * <code>ibis.properties</code> in the current directory. This allows the user
 * to set some properties that he/she wants for all his/her applications in a
 * property file in his/her home directory, and to also set some
 * application-specific properties in the current directory.
 */
public final class IbisProperties {

    /** Filename for the properties. */
    public static final String PROPERTIES_FILENAME = "ibis.properties";

    /** All our own properties start with this prefix. */
    public static final String PREFIX = "ibis.";

    /** Property name for selecting an Ibis. */
    public static final String NAME = PREFIX + "name";

    /** Property name of the property file. */
    public static final String PROPERTIES_FILE = PREFIX + "properties.file";

    /** Property name for the path used to find Ibis implementations. */
    public static final String IMPL_PATH = PREFIX + "impl.path";

    /** Property name for verbosity. */
    public static final String VERBOSE = PREFIX + "verbose";

    public static final String POOL_NAME = PREFIX + "pool.name";

    public static final String POOL_SIZE = PREFIX + "pool.size";

    public static final String SERVER_ADDRESS = PREFIX + "server.address";

    public static final String HUB_ADDRESSES = PREFIX + "hub.addresses";

    public static final String REGISTRY_IMPL = PREFIX + "registry.impl";

    /**
     * Property name for location.
     */
    public static final String LOCATION = PREFIX + "location";

    public static final String LOCATION_AUTOMATIC =
            PREFIX + "location.automatic";

    public static final String LOCATION_POSTFIX =
        PREFIX + "location.postfix";

    /** List of {NAME, DESCRIPTION, DEFAULT_VALUE} for properties. */
    private static final String[][] propertiesList =
            new String[][] {
                { POOL_NAME, null, "String: name of the pool this ibis belongs to" },
                { POOL_SIZE, null,
                    "Integer: size of the pool this ibis belongs to" },
                { SERVER_ADDRESS, null, "address of the central ibis server" },
                {
                    HUB_ADDRESSES,
                    null,
                    "comma seperated list of hub addresses."
                            + " The server address is appended to this list,"
                            + " and thus is the default hub if no hub is specified" },

                { NAME, null,
                    "Nickname or classname of the Ibis implementation" },

                { PROPERTIES_FILE, null,
                    "Name of the property file used for the configuration of Ibis" },

                { IMPL_PATH, null, "Path used to find Ibis implementations" },

                { VERBOSE, "false",
                    "Boolean: If true, makes Ibis more verbose, if false, does not" },

                { LOCATION, null,
                    "Set the location of Ibis. Specified as multiple levels, "
                            + "seperated by a '@', e.g. machine@cluster@site@grid@world."
                            + " Defaults to a single level location with the hostname of the machine" },

                { LOCATION_AUTOMATIC, "false",
                    "Boolean: If true, a multi level location is automatically created" },

                { LOCATION_POSTFIX, null,
                    "Set a string that will be appended to the automatically generated location." },

                { REGISTRY_IMPL, "ibis.ipl.impl.registry.central.Registry",
                    "implementation of the registry. Not all Ibis implementations use this property" },

            };

    private static Properties defaultProperties;

    /**
     * Private constructor, to prevent construction of an IbisProperties object.
     */
    private IbisProperties() {
        // nothing
    }

    /**
     * Returns the built-in properties of Ibis.
     * 
     * @return the resulting properties.
     */
    public static Properties getHardcodedProperties() {
        Properties properties = new Properties();

        for (String[] element : propertiesList) {
            if (element[1] != null) {
                properties.setProperty(element[0], element[1]);
            }
        }

        return properties;
    }

    /**
     * Returns a map mapping hard-coded property names to their descriptions.
     * 
     * @return the name/description map.
     */
    public static Map<String, String> getDescriptions() {
        Map<String, String> result = new LinkedHashMap<String, String>();

        for (String[] element : propertiesList) {
            result.put(element[0], element[2]);
        }

        return result;
    }

    private static Properties getPropertyFile(String file) {

        InputStream in = null;

        try {
            in = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            // ignored
        }

        if (in == null) {

            ClassLoader loader = ClassLoader.getSystemClassLoader();

            in = loader.getResourceAsStream(file);

            if (in == null) {
                return null;
            }
        }

        try {
            Properties p = new Properties();
            p.load(in);
            return p;
        } catch (IOException e) {
            try {
                in.close();
            } catch (Exception x) {
                // ignore
            }
        }
        return null;
    }

    public static Properties getDefaultProperties() {
        
        if (defaultProperties == null) { 
            defaultProperties = getHardcodedProperties();

            // Get the properties from the commandline. 
            Properties system = System.getProperties();

            // Check what property file we should load.
            String file = system.getProperty(PROPERTIES_FILE,
                    PROPERTIES_FILENAME);

            // If the file is not explicitly set to null, we try to load it.
            // First try the filename as is, if this fails try with the
            // user home directory prepended.
            if (file != null) {
                Properties fromFile = getPropertyFile(file);
                if (fromFile != null) {
                    defaultProperties.putAll(fromFile);
                } else {
                    String homeFn = System.getProperty("user.home")
                        + System.getProperty("file.separator") + file;
                    fromFile = getPropertyFile(homeFn);
                    
                    if (fromFile == null) { 
                        if (! file.equals(PROPERTIES_FILENAME)) { 
                            // If we fail to load the user specified file,
                            // we give an error, since only the default file
                            // may fail silently.                     
                            System.err.println("User specified preferences \""
                                    + file + "\" not found!");
                        }                                            
                    } else {                  
                        // If we managed to load the file, we add the
                        // properties to the 'defaultProperties' possibly
                        // overwriting defaults.
                        defaultProperties.putAll(fromFile);
                    }
                }
            }
            
            // Finally, add the system properties (also from the command line)
            // to the result, possibly overriding entries from file or the 
            // defaults.            
            defaultProperties.putAll(system);
        } 
        
        return new Properties(defaultProperties);        
    }
}
