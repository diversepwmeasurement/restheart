/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.Maps;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import static org.restheart.configuration.Utils.*;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.jxpath.JXPathContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Class that holds the configuration.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class Configuration {

    /**
     * the version is read from the JAR's MANIFEST.MF file, which is automatically
     * generated by the Maven build process
     */
    public static final String VERSION = Configuration.class.getPackage().getImplementationVersion() == null
        ? "unknown, not packaged"
        : Configuration.class.getPackage().getImplementationVersion();

    static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    public static final String DEFAULT_ROUTE = "0.0.0.0";

    /**
     * hold the path of the configuration file
     */
    private static Path PATH = null;

    private static final Listener DEFAULT_HTTP_LISTENER = new Listener(true, "localhost", 8080);
    private static final TLSListener DEFAULT_HTTPS_LISTENER = new TLSListener(false, "localhost", 4443, null, null, null);
    private static final Listener DEFAULT_AJP_LISTENER = new Listener(false, "localhost", 8009);

    /**
     * undertow connetction options
     *
     * See
     * http://undertow.io/undertow-docs/undertow-docs-1.3.0/index.html#common-listener-optionshttp://undertow.io/undertow-docs/undertow-docs-1.3.0/index.html#common-listener-options
     */
    public static final String CONNECTION_OPTIONS_KEY = "connection-options";

    private final Listener httpListener;
    private final Listener ajpListener;
    private final TLSListener httpsListener;
    private final List<ProxiedResource> proxies;
    private final List<StaticResource> staticResources;
    private final CoreModule coreModule;
    private final Logging logging;
    private final Map<String, Object> connectionOptions;

    private Map<String, Object> conf;

    /**
     * Creates a new instance of Configuration from the configuration file For any
     * missing property the default value is used.
     *
     * @param conf   the key-value configuration map
     * @param silent
     * @throws org.restheart.configuration.ConfigurationException
     */
    private Configuration(Map<String, Object> conf, final Path confFilePath, boolean silent) throws ConfigurationException {
        PATH = confFilePath;

        this.conf = conf;

        this.coreModule = CoreModule.build(conf, silent);


        if (findOrDefault(conf, Listener.HTTP_LISTENER_KEY, null, true) != null) {
            httpListener = new Listener(conf, Listener.HTTP_LISTENER_KEY, DEFAULT_HTTP_LISTENER, silent);
        } else {
            httpListener = DEFAULT_HTTP_LISTENER;
        }

        if (findOrDefault(conf, TLSListener.HTTPS_LISTENER_KEY, null, true) != null) {
            httpsListener = new TLSListener(conf, TLSListener.HTTPS_LISTENER_KEY, DEFAULT_HTTPS_LISTENER, silent);
        } else {
            httpsListener = DEFAULT_HTTPS_LISTENER;
        }

        if (findOrDefault(conf, Listener.AJP_LISTENER_KEY, null, true) != null) {
            ajpListener = new Listener(conf, Listener.AJP_LISTENER_KEY, DEFAULT_AJP_LISTENER, silent);
        } else {
            ajpListener = DEFAULT_AJP_LISTENER;
        }

        proxies = ProxiedResource.build(conf, silent);

        staticResources = StaticResource.build(conf, silent);

        logging = Logging.build(conf, silent);

        connectionOptions = asMap(conf, CONNECTION_OPTIONS_KEY, null, silent);
    }

    @Override
    public String toString() {
        var dumpOpts = new DumperOptions();
        dumpOpts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumpOpts.setPrettyFlow(true);
        dumpOpts.setIndent(2);
        dumpOpts.setCanonical(false);
        dumpOpts.setExplicitStart(true);

        var sw = new StringWriter();
        new Yaml(dumpOpts).dump(conf, sw);

        return sw.toString();
    }

    public <V extends Object> V getOrDefault(final String key, final V defaultValue) {
        return Utils.getOrDefault(this, key, defaultValue, true);
    }

    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(this.conf);
    }

    public CoreModule coreModule() {
        return coreModule;
    }

    /**
     * @return the proxies
     */
    public List<ProxiedResource> getProxies() {
        return Collections.unmodifiableList(proxies);
    }

    /**
     * @return the staticResources
     */
    public List<StaticResource> getStaticResources() {
        return Collections.unmodifiableList(staticResources);
    }

    /**
     * @return the httpListener
     */
    public Listener httpListener() {
        return httpListener;
    }

    /**
     * @return the httpsListener
     */
    public TLSListener httpsListener() {
        return httpsListener;
    }

    /**
     * @return the ajpListener
     */
    public Listener ajpListener() {
        return ajpListener;
    }


    /**
     * @return the logLevel
     */
    public Level getLogLevel() {
        var logbackConfigurationFile = System.getProperty("logback.configurationFile");
        if (logbackConfigurationFile != null && !logbackConfigurationFile.isEmpty()) {
            var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            var logger = loggerContext.getLogger("org.restheart");
            return logger.getLevel();
        }

        return logging.logLevel();
    }

    public Logging logging() {
        return logging;
    }

    /**
     * @return the connectionOptions
     */
    public Map<String, Object> getConnectionOptions() {
        return Collections.unmodifiableMap(connectionOptions);
    }

    /**
     *
     * @return the path of the configuration file
     */
    public static Path getPath() {
        return PATH;
    }

    static boolean isParametric(final Path confFilePath) throws IOException {
        try (var sc = new Scanner(confFilePath, "UTF-8")) {
            return sc.findAll(Pattern.compile("\\{\\{.*\\}\\}")).limit(1).count() > 0;
        }
    }

    public class Builder {
        /**
         *
         * @return the default configuration
         */
        public static Configuration build(boolean standaloneConfiguration, boolean silent) {
            return build(null, null, standaloneConfiguration, silent);
        }

        /**
         *
         * @param confFile
         * @return return the configuration from confFile and propFile
         */
        public static Configuration build(Path confFilePath, Path confOverridesFilePath, boolean standaloneConfiguration, boolean silent) throws ConfigurationException {
            if (confFilePath == null) {
                var defaultConfFilePath = standaloneConfiguration ? "/restheart-default-config-no-mongodb.yml" : "/restheart-default-config.yml";
                var stream = Configuration.class.getResourceAsStream(defaultConfFilePath);
                try (var confReader = new InputStreamReader(stream)) {
                    return build(confReader, null, confOverridesFilePath, silent);
                } catch (IOException ieo) {
                    throw new ConfigurationException("Error reading default configuration file", ieo);
                }
            } else {
                try (var confReader = new BufferedReader(new FileReader(confFilePath.toFile()))) {
                    return build(confReader, confFilePath, confOverridesFilePath, silent);
                } catch (FileNotFoundException ex) {
                    throw new ConfigurationException("Configuration file not found: " + confFilePath, ex, false);
                } catch (IOException ieo) {
                    throw new ConfigurationException("Error reading configuration file " + confFilePath, ieo);
                }
            }
        }

        /**
         *
         * @param confFile
         * @return return the configuration from confFile and propFile
         */
        private static Configuration build(Reader confReader, Path confFilePath, Path confOverridesFilePath, boolean silent) throws ConfigurationException {
            Map<String, Object> confMap = new Yaml(new SafeConstructor(new LoaderOptions())).load(confReader);

            if (confOverridesFilePath != null) {
                try {
                    String overrides;

                    if (confOverridesFilePath.toString().toLowerCase().endsWith(".yml")
                        || confOverridesFilePath.toString().toLowerCase().endsWith(".yaml")) {
                        // YML format
                        try {
                            overrides = fromYmlToRho(Files.newBufferedReader(confOverridesFilePath));
                        } catch(JsonParseException jpe) {
                            throw new ConfigurationException("Wrong configuration override YML file: " + jpe.getLocalizedMessage(), jpe, false);
                        }
                    } else if (confOverridesFilePath.toString().toLowerCase().endsWith(".json")
                        || confOverridesFilePath.toString().toLowerCase().endsWith(".jsonc")) {
                        // JSON format
                        try {
                            overrides = fromJsonToRho(Files.newBufferedReader(confOverridesFilePath));
                        } catch(JsonParseException jpe) {
                            throw new ConfigurationException("Wrong configuration override JSON file: " + jpe.getLocalizedMessage(), jpe, false);
                        }
                    } else if (confOverridesFilePath.toString().toLowerCase().endsWith(".conf")) {
                        // RHO format
                        overrides = Files.readAllLines(confOverridesFilePath).stream()
                            .filter(row -> !row.trim().startsWith("#")) // ingore comments lines
                            .collect(Collectors.joining());
                    } else {
                        throw new ConfigurationException("Configuration override file must have .json, .jsonc, .yml, .yaml or .conf extension: " + confOverridesFilePath);
                    }

                    if (!silent) {
                        LOGGER.info("Overriding configuration from file: {}", confOverridesFilePath);
                    }

                    confMap = overrideConfiguration(confMap, overrides(overrides, true, silent), silent);
                } catch (IOException ioe) {
                    throw new ConfigurationException("Configuration override file not found: " + confOverridesFilePath, ioe, false);
                }
            }

            // overrides with RHO env var
            if (System.getenv().containsKey("RHO")) {
                if (!silent) {
                    LOGGER.info("Overriding configuration from environment variable RHO");
                }

                // overrides from RHO env var
                confMap = overrideConfiguration(confMap, overrides(System.getenv().get("RHO"), true, silent), silent);
            }

            return new Configuration(confMap, confFilePath, silent);
        }
    }

    // converst a JSON configuration override file into the RHO syntax
    // { "/logging/log-level": "INFO", "/core/name": "foo" } => /logging/log-level->"INFO";/core/name->"foo";
    private static String fromJsonToRho(Reader jsonReader) throws JsonParseException {
        var gson = new GsonBuilder().setLenient().create(); // lenient allows JSON with comments
        var _json = gson.fromJson(jsonReader, JsonObject.class);

        if (_json == null || !_json.isJsonObject()) {
            throw new JsonParseException("json is not an object");
        }

        var obj = _json.getAsJsonObject();

        return obj.entrySet().stream()
            .map(e -> e.getKey() + "->" + e.getValue().toString())
            .collect(Collectors.joining(";"));
    }

    // converst a YMM configuration override file into the RHO syntax
    // /logging/log-level: "INFO"
    // /core/name: "foo" => /logging/log-level->"INFO";/core/name->"foo";
    private static String fromYmlToRho(Reader yml) throws JsonParseException {
        Map<String, Object> _yml = new Yaml(new SafeConstructor(new LoaderOptions())).load(yml);

        if (_yml == null) {
            throw new JsonParseException("json is not an object");
        }

        final var gson = new GsonBuilder().serializeNulls().create();

        return _yml.entrySet().stream()
            .map(e -> e.getKey() + "->" + gson.toJson(e.getValue()).toString())
            .collect(Collectors.joining(";"));
    }

    /**
     *
     * @param confMap
     * @return
     */
    private static Map<String, Object> overrideConfiguration(Map<String, Object> confMap, List<RhOverride> overrides, final boolean silent) {
        var ctx = JXPathContext.newContext(confMap);
        ctx.setLenient(true);

        overrides.stream().forEachOrdered(o -> {
            if (!silent) {
                if (o.path().contains("password") || o.path().contains("pwd") || o.path().contains("secret")) {
                    LOGGER.info("\t{} -> {}", o.path(), "**********");
                } else {
                    LOGGER.info("\t{} -> {}", o.path(), o.value());
                }
            }

            if (!o.path().startsWith("/")) {
                LOGGER.error("Wrong configuration override {}, path must start with /", o.raw());
            } else {
                try {
                    createPathAndSetValue(ctx, o.path(), o.value());
                } catch(Throwable ise) {
                    LOGGER.error("Wrong configuration override {}, {}", o.raw(), ise.getMessage());
                }
            }
        });

        return confMap;
    }

    private static void createPathAndSetValue(JXPathContext ctx, String path, Object value) {
        createParents(ctx, path);
        ctx.createPathAndSetValue(path, value);
    }

    private static void createParents(JXPathContext ctx, String path) {
        var parentPath = path.substring(0, path.lastIndexOf("/"));

        if (!parentPath.equals("")) {
            createParents(ctx, parentPath);
        }

        var array = path.trim().endsWith("]");

        if (array) {
            // /a/b[2] -> /a/b
            var arrayPath = path.substring(0, path.lastIndexOf("["));
            if (ctx.getValue(arrayPath) == null) {
                ctx.createPathAndSetValue(arrayPath, new ArrayList<>());
            }
        } else {
            if (ctx.getValue(path) == null) {
                ctx.createPathAndSetValue(path, Maps.newLinkedHashMap());
            }
        }
    }
}
