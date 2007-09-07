/* $Id$ */

package ibis.ipl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * This is the class responsible for starting an Ibis instance. During
 * initialization, this class determines which Ibis implementations are
 * available. It does so, by finding all jar files in either the class path or
 * all jar files in the directories indicated by the ibis.ipl.impl.path
 * property. All Ibis implementations should be mentioned in the main properties
 * of the manifest of the jar file containing it, in the "Ibis-Starter"
 * entry. This entry should contain a comma- or space-separated list of class
 * names, where each class named provides an {@link IbisStarter} implementation.
 * In addition, a property "Ibis-Version" should be defined in the manifest,
 * containing a version number starting with 2.0.
 */
public final class IbisFactory {

    // Default configuration properties, from ibis.properties file et al.
    private static Properties defaultConfigProperties;

    // Map of factories. One for each implementation path
    private static final Map<String, IbisFactory> factories =
            new HashMap<String, IbisFactory>();
    private static IbisFactory defaultFactory;

    static final String VERSION = "2.0";

    /**
     * Adds the properties as loaded from the specified stream to the specified
     * properties.
     * 
     * @param inputStream
     *            the input stream.
     * @param properties
     *            the properties.
     */
    private static void load(InputStream inputStream, Properties properties) {
        if (inputStream != null) {
            try {
                properties.load(inputStream);
            } catch (IOException e) {
                // ignored
            } finally {
                try {
                    inputStream.close();
                } catch (Throwable e1) {
                    // ignored
                }
            }
        }
    }

    /**
     * Load properties from the standard configuration file locations.
     */
    private static synchronized Properties getDefaultProperties() {
        if (defaultConfigProperties == null) {
            defaultConfigProperties = new Properties();

            // Load properties from the classpath
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            InputStream inputStream =
                    classLoader
                            .getResourceAsStream(IbisProperties.PROPERTIES_FILENAME);
            load(inputStream, defaultConfigProperties);

            // See if there is an ibis.properties file in the current
            // directory.
            try {
                inputStream =
                        new FileInputStream(IbisProperties.PROPERTIES_FILENAME);
                load(inputStream, defaultConfigProperties);
            } catch (FileNotFoundException e) {
                // ignored
            }

            Properties systemProperties = System.getProperties();

            // Then see if the user specified an properties file.
            String file =
                    systemProperties
                            .getProperty(IbisProperties.PROPERTIES_FILE);
            if (file != null) {
                try {
                    inputStream = new FileInputStream(file);
                    load(inputStream, defaultConfigProperties);
                } catch (FileNotFoundException e) {
                    System.err.println("User specified preferences \"" + file
                            + "\" not found!");
                }
            }

            // Finally, add the properties from the command line to the result,
            // possibly overriding entries from file or the defaults.
            for (Enumeration e = systemProperties.propertyNames(); e
                    .hasMoreElements();) {
                String key = (String) e.nextElement();
                String value = systemProperties.getProperty(key);
                defaultConfigProperties.setProperty(key, value);
            }
        }

        return defaultConfigProperties;
    }

    private static synchronized IbisFactory getFactory(String implPath) {
        if (implPath == null) {
            if (defaultFactory == null) {
                defaultFactory = new IbisFactory(null);
            }
            
            return defaultFactory;
        } else {
            IbisFactory factory = factories.get(implPath);

            if (factory == null) {
                factory = new IbisFactory(implPath);
                factories.put(implPath, factory);
            }
            
            return factory;
        }
    }

    private Class[] implList;
    private IbisStarter[] starters;
    private boolean verbose = false;
    private String ibisName = null;

    /**
     * Constructs an Ibis factory, with the specified search path.
     * 
     * @param implPath
     *            the path to search for implementations.
     */
    private IbisFactory(String implPath) {
        // Obtain a list of Ibis implementations
        ClassLister clstr = ClassLister.getClassLister(implPath);
        List<Class> compnts =
                clstr.getClassList("Ibis-Starter", IbisStarter.class, VERSION);
        implList = compnts.toArray(new Class[compnts.size()]);
        starters = new IbisStarter[implList.length];
    }

    /**
     * Creates a new Ibis instance, based on the required capabilities and port
     * types, and using the specified properties.
     * 
     * @param requiredCapabilities
     *            ibis capabilities required by the application.
     * @param properties
     *            properties that can be set, for instance a class path for
     *            searching ibis implementations, or which registry to use.
     *            There is a default, so <code>null</code> may be specified.
     * @param addDefaultConfigProperties
     *            TODO
     * @param registryEventHandler
     *            a {@link ibis.ipl.RegistryEventHandler RegistryEventHandler}
     *            instance, or <code>null</code>.
     * @param portTypes
     *            the list of port types required by the application.
     * @return the new Ibis instance.
     * 
     * @exception IbisCreationFailedException
     *                is thrown when no Ibis was found that matches the
     *                capabilities required.
     */
    public static Ibis createIbis(IbisCapabilities requiredCapabilities,
            Properties properties, boolean addDefaultConfigProperties,
            RegistryEventHandler registryEventHandler, PortType... portTypes)
            throws IbisCreationFailedException {

        Properties combinedProperties = new Properties();

        // add default properties, if required
        if (addDefaultConfigProperties) {
            Properties defaults = getDefaultProperties();

            for (Enumeration e = defaults.propertyNames(); e.hasMoreElements();) {
                String key = (String) e.nextElement();
                String value = defaults.getProperty(key);
                combinedProperties.setProperty(key, value);
            }
        }

        // add user properties
        if (properties != null) {
            for (Enumeration e = properties.propertyNames(); e
                    .hasMoreElements();) {
                String key = (String) e.nextElement();
                String value = properties.getProperty(key);
                combinedProperties.setProperty(key, value);
            }
        }
        
        String implPath =
                combinedProperties.getProperty(IbisProperties.IMPL_PATH);
        IbisFactory factory = getFactory(implPath);

        return factory.createIbis(registryEventHandler, requiredCapabilities,
                        combinedProperties, portTypes);
    }

    private List<IbisStarter> findIbisStack(IbisCapabilities capabilities,
            PortType[] portTypes, List<IbisStarter> selected, String ibisName) {

        IbisCapabilities caps = capabilities;
        PortType[] types = portTypes;

        // First try non-stacking Ibis implementations.
        for (int i = 0; i < starters.length; i++) {
            IbisStarter starter = starters[i];
            // If it is selectable, or an Ibis name was specified,
            // try it.
            if ((starter.isSelectable() || ibisName != null) &&
                    ! starter.isStacking()) {
                if (verbose) {
                    System.err.println("Matching with " + implList[i]);
                }
                if (starter.matches(caps, types)) {
                    selected.add(starter);
                    return selected;
                }
                // Find out why it did not match.
                if (verbose) {
                    String unmatchedCapabilities
                        = starter.unmatchedIbisCapabilities().toString();
                    PortType[] unmatchedTypes
                        = starter.unmatchedPortTypes();
                    StringBuffer str = new StringBuffer();
                    str.append("Unmatched IbisCapabilities: ");
                    str.append(unmatchedCapabilities);
                    if (unmatchedTypes.length > 0) {
                        str.append("\nUnmatched PortTypes: ");
                        for (PortType tp: unmatchedTypes) {
                            str.append("    ");
                            str.append(tp.toString());
                            str.append("\n");
                        }
                    } else {
                        str.append("\n");
                    }
                    System.err.println("Class " + implList[i]
                            + " does not match:\n" + str.toString());
                }
            } else if (verbose) {
                System.err.println("Class " + implList[i]
                            + " is stacking or not selectable.");
            }
        }

        // Now try stacking Ibis implementations.
        for (int i = 0; i < starters.length; i++) {
            IbisStarter starter = starters[i];
            if ((starter.isSelectable() || ibisName != null)
                    && starter.isStacking() && ! selected.contains(starter)
                    && starter.matches(caps, types)) {
                if (verbose) {
                    System.err.println("Class " + implList[i]
                            + " selected");
                }
                List<IbisStarter> newList = findIbisStack(
                        new IbisCapabilities(starter.unmatchedIbisCapabilities()),
                        starter.unmatchedPortTypes(),
                        new ArrayList<IbisStarter>(selected),
                        null);
                if (newList != null) {
                    return newList;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Ibis createIbis(RegistryEventHandler registryEventHandler,
            IbisCapabilities requiredCapabilities, Properties properties,
            PortType[] portTypes) throws IbisCreationFailedException {

        String verboseValue = properties.getProperty(IbisProperties.VERBOSE);
        // see if the user specified "verbose"
        verbose =
                verboseValue != null
                        && (verboseValue.equals("1")
                                || verboseValue.equals("on")
                                || verboseValue.equals("")
                                || verboseValue.equals("true") || verboseValue
                                .equals("yes"));

        if (verbose) {
            System.err.println("Looking for an Ibis with capabilities: "
                    + requiredCapabilities);
            System.err.println("(ibis) Properties:");
            for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
                String key = (String) e.nextElement();
                if (key.startsWith("ibis")) {
                    String value = properties.getProperty(key);
                    System.err.println(key + " = " + value);
                }
            }
        }

        ibisName = properties.getProperty(IbisProperties.NAME);

        if (ibisName != null) {
            String[] capabilities = requiredCapabilities.getCapabilities();
            String[] newCapabilities = new String[capabilities.length + 1];
            for (int i = 0; i < capabilities.length; i++) {
                newCapabilities[i] = capabilities[i];
            }
            newCapabilities[capabilities.length] = "nickname." + ibisName;
            capabilities = newCapabilities;
            requiredCapabilities = new IbisCapabilities(newCapabilities);
        }


        if (verbose) {
            StringBuffer str = new StringBuffer();
            str.append("Ibis implementations:");
            for (int i = 0; i < implList.length; i++) {
                str.append(" ");
                str.append(implList[i].getName());
            }
            System.err.println(str.toString());
        }

        IbisCreationFailedException nested =
                new IbisCreationFailedException("Ibis creation failed");

        //
        // Factory does some initial sanity checks.
        // Port types can only specify a single connection capability,
        // and must specify a serialization.
        
        boolean faulty = false;
        for (PortType tp: portTypes) {
            // Check sanity of port types.
            int cnt = 0;
            if (tp.hasCapability(PortType.CONNECTION_MANY_TO_MANY)) {
                cnt++;
            }
            if (tp.hasCapability(PortType.CONNECTION_ONE_TO_ONE)) {
                cnt++;
            }
            if (tp.hasCapability(PortType.CONNECTION_ONE_TO_MANY)) {
                cnt++;
            }
            if (tp.hasCapability(PortType.CONNECTION_MANY_TO_ONE)) {
                cnt++;
            }
            if (cnt != 1) {
                nested.add("Ibis factory",
                        new IbisConfigurationException("PortType " + tp
                            + " should specify exactly one connection type"));
                faulty = true;
            }
            String[] caps = tp.getCapabilities();
            boolean ok = false;
            for (String s : caps) {
                if (s.startsWith(PortType.SERIALIZATION)) {
                    ok = true;
                    break;
                }
            }
            if (! ok) {
                nested.add("Ibis factory",
                    new IbisConfigurationException("Port type " + tp
                        + " should specify serialization"));
                faulty = true;
            }
        }

        // If a registryEventHandler is specified, the membership capability
        // must be requested as well.

        if (registryEventHandler != null
                && !requiredCapabilities.hasCapability(
                    IbisCapabilities.MEMBERSHIP)) {
            nested.add("Ibis factory", new IbisConfigurationException(
                    "RegistryEventHandler specified but no "
                            + IbisCapabilities.MEMBERSHIP
                            + " capability requested"));
            faulty = true;
        }

        if (faulty) {
            // There is some error in the user-specified capabilities or
            // port types.
            throw nested;
        }

        for (int i = 0; i < implList.length; i++) {
            Class starterClass = implList[i];
            if (verbose) {
                System.err.println("Trying " + starterClass.getName());
            }

            // Try to instantiate the starter.
            try {
                starters[i] = (IbisStarter) starterClass.newInstance();
            } catch (Throwable e) {
                // Oops, could not instantiate starter.
                nested.add(starterClass.getName(), e);
                faulty = true;
                if (verbose) {
                    System.err.println("Could not instantiate "
                            + starterClass.getName() + ": " + e);
                }
                continue;
            }
        }

        if (faulty) {
            // There is some error in the configuration: one or more of
            // the starter classes could not be instantiated.
            throw nested;
        }
        
        List<IbisStarter> stack = findIbisStack(requiredCapabilities,
                portTypes, new ArrayList<IbisStarter>(), ibisName);
        
        if (stack != null) {
            IbisStarter starter = stack.remove(0);
            return starter.startIbis(stack, registryEventHandler, properties);
        }

        nested.add("Ibis factory",
                new IbisConfigurationException("No matching Ibis found"));
        throw nested;
    }

    /**
     * This class exports a method for searching either the classpath or a
     * specified list of directories for jar-files with a specified name in the
     * Manifest.
     */
    private static class ClassLister {

        private JarFile[] jarFiles;

        private ClassLoader ld = null;

        private static HashMap<String, ClassLister> listers =
                new HashMap<String, ClassLister>();

        private static ClassLister classPathLister = null;

        /**
         * Constructs a <code>ClassLister</code> from the specified directory
         * list. All jar files found in the specified directories are used. if
         * <code>dirList</code> is <code>null</code>, all jar files from
         * the classpath are used instead.
         * 
         * @param dirList
         *            a list of directories, or <code>null</code>, in which
         *            the classpath is used to find jar files.
         */
        private ClassLister(String dirList) {
            if (dirList != null) {
                readJarFiles(dirList);
            } else {
                readJarFiles();
            }

            URL[] urls = new URL[jarFiles.length];

            for (int i = 0; i < jarFiles.length; i++) {
                try {
                    File f = new File(jarFiles[i].getName());
                    urls[i] = f.toURI().toURL();
                } catch (Exception e) {
                    throw new Error(e);
                }
            }

            ld = new URLClassLoader(urls, this.getClass().getClassLoader());
        }

        /**
         * Obtains a <code>ClassLister</code> for the specified directory
         * list. All jar files found in the specified directories are used. if
         * <code>dirList</code> is <code>null</code>, all jar files from
         * the classpath are used instead.
         * 
         * @param dirList
         *            a list of directories, or <code>null</code>, in which
         *            the classpath is used to find jar files.
         * @return the required <code>ClassLister</code>.
         */
        private static synchronized ClassLister getClassLister(String dirList) {
            if (dirList == null) {
                if (classPathLister == null) {
                    classPathLister = new ClassLister(null);
                }
                return classPathLister;
            }

            ClassLister lister = listers.get(dirList);
            if (lister == null) {
                lister = new ClassLister(dirList);
                listers.put(dirList, lister);
            }
            return lister;
        }

        /**
         * This method reads all jar files from the classpath, and stores them
         * in a list that can be searched for specific names later on.
         */
        protected void readJarFiles() {
            ArrayList<JarFile> jarList = new ArrayList<JarFile>();
            String classPath = System.getProperty("java.class.path");
            if (classPath != null) {
                StringTokenizer st =
                        new StringTokenizer(classPath, File.pathSeparator);
                while (st.hasMoreTokens()) {
                    String jar = st.nextToken();
                    File f = new File(jar);
                    try {
                        JarFile jarFile = new JarFile(f, true);
                        Manifest manifest = jarFile.getManifest();
                        if (manifest != null) {
                            manifest.getMainAttributes();
                            jarList.add(jarFile);
                        }
                    } catch (IOException e) {
                        // ignore. Could be a directory.
                    }
                }
            }
            jarFiles = jarList.toArray(new JarFile[0]);
        }

        private void addJarFiles(String dir, ArrayList<JarFile> jarList) {
            File f = new File(dir);
            File[] files = f.listFiles();
            if (files == null) {
                return;
            }
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()) {
                    try {
                        JarFile jarFile = new JarFile(files[i], true);
                        Manifest manifest = jarFile.getManifest();
                        if (manifest != null) {
                            manifest.getMainAttributes();
                            jarList.add(jarFile);
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }

        /**
         * This method reads all jar files found in the specified directories,
         * and stores them in a list that can be searched for specific names
         * later on.
         * 
         * @param dirList
         *            list of directories to search, separator is
         *            <code>java.io.File.pathSeparator</code>.
         */
        protected void readJarFiles(String dirList) {
            ArrayList<JarFile> jarList = new ArrayList<JarFile>();

            StringTokenizer st =
                    new StringTokenizer(dirList, File.pathSeparator);

            while (st.hasMoreTokens()) {
                String dir = st.nextToken();
                addJarFiles(dir, jarList);
            }
            jarFiles = jarList.toArray(new JarFile[0]);
        }

        /**
         * Returns a list of classes for the specified attribute name. The
         * specified manifest attribute name is assumed to be mapped to a
         * comma-separated list of class names. All jar files in the classpath
         * are scanned for the specified manifest attribute name, and the
         * attribute values are loaded.
         * 
         * @param attribName
         *            the manifest attribute name.
         * @param version
         *            required version, or null.
         * @return the list of classes.
         */
        private List<Class> getClassList(String attribName, String version) {
            ArrayList<Class> list = new ArrayList<Class>();

            for (int i = 0; i < jarFiles.length; i++) {
                Manifest mf = null;
                try {
                    mf = jarFiles[i].getManifest();
                } catch (IOException e) {
                    throw new Error("Could not get Manifest from "
                            + jarFiles[i].getName(), e);
                }
                if (mf != null) {
                    Attributes ab = mf.getMainAttributes();
                    if (version != null) {
                        String jarVersion = ab.getValue("Ibis-Version");
                        if (jarVersion == null
                                || ! jarVersion.startsWith(version)) {
                            continue;
                        }
                    }
                    String classNames = ab.getValue(attribName);
                    if (classNames != null) {
                        StringTokenizer st =
                                new StringTokenizer(classNames, ", ");
                        while (st.hasMoreTokens()) {
                            String className = st.nextToken();
                            try {
                                Class cl = Class.forName(className, false, ld);
                                list.add(cl);
                            } catch (Exception e) {
                                throw new Error("Could not load class "
                                        + className
                                        + ". Something wrong with jar "
                                        + jarFiles[i].getName() + "?", e);
                            }
                        }
                    }
                }
            }
            return list;
        }

        /**
         * Returns a list of classes for the specified attribute name. The
         * specified manifest attribute name is assumed to be mapped to a
         * comma-separated list of class names. All jar files in the classpath
         * are scanned for the specified manifest attribute name, and the
         * attribute values are loaded. The classes thus obtained should be
         * extensions of the specified class, or, if it is an interface,
         * implementations of it.
         * 
         * @param attribName
         *            the manifest attribute name.
         * @param clazz
         *            the class of which the returned classes are
         *            implementations or extensions.
         * @param version
         *            required version, or null.
         * @return the list of classes.
         */
        private List<Class> getClassList(String attribName, Class<?> clazz,
                String version) {
            List<Class> list = getClassList(attribName, version);

            for (Class<?> cl : list) {
                if (!clazz.isAssignableFrom(cl)) {
                    throw new Error("Class " + cl.getName()
                            + " cannot be assigned to class " + clazz.getName());
                }
            }
            return list;
        }
    }
}
