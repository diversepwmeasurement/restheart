/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.RED;

import org.restheart.Bootstrapper;
import org.restheart.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BootstrapperUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrapper.class);

    private static final String INSTANCE = " instance ";
    private static final String STARTING = "Starting ";
    private static final String RESTHEART = "RESTHeart";
    private static final String VERSION = "Version {}";
    private static final String UNDEFINED = "undefined";

    public static String getInstanceName(Configuration configuration) {
        return configuration == null ? UNDEFINED
            : configuration.instanceName() == null
            ? UNDEFINED
            : configuration.instanceName();
    }

    /**
     * sets the Configuration for JsonPath
     */
    public static void setJsonpathDefaults() {
        com.jayway.jsonpath.Configuration.setDefaults(new com.jayway.jsonpath.Configuration.Defaults() {
            private final JsonProvider jsonProvider = new GsonJsonProvider();
            private final MappingProvider mappingProvider = new GsonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<com.jayway.jsonpath.Option> options() {
                return EnumSet.noneOf(com.jayway.jsonpath.Option.class);
            }
        });
    }

    /**
     * logs warning message if pid file exists
     *
     * @param confFilePath
     * @param propFilePath
     * @return true if pid file exists
     */
    public static boolean checkPidFile(Path confFilePath, Path propFilePath) {
        if (OSChecker.isWindows()) {
            return false;
        }

        // pid file name include the hash of the configuration file so that
        // for each configuration we can have just one instance running
        var pidFilePath = pidFile(confFilePath, propFilePath);

        if (Files.exists(pidFilePath)) {
            LOGGER.warn("Found pid file! If this instance is already running, startup will fail with a BindException");
            return true;
        }
        return false;
    }

    public static Path pidFile(Path confFilePath, Path propFilePath) {
        return FileUtils.getPidFilePath(FileUtils.getFileAbsolutePathHash(confFilePath, propFilePath));
    }

    /**
     * initLogging
     *
     * @param args
     * @param d
     */
    public static void initLogging(Configuration configuration, final RESTHeartDaemon d, boolean isForked) {
        LoggingInitializer.setLogLevel(configuration.getLogLevel());
        if (d != null && d.isDaemonized()) {
            LoggingInitializer.stopConsoleLogging();
            LoggingInitializer.startFileLogging(configuration.getLogFilePath());
        } else if (!isForked) {
            if (!configuration.isLogToConsole()) {
                LoggingInitializer.stopConsoleLogging();
            }
            if (configuration.isLogToFile()) {
                LoggingInitializer.startFileLogging(configuration.getLogFilePath());
            }
        }
    }

    public static void logStartMessages(Configuration configuration) {
        var instanceName = getInstanceName(configuration);
        LOGGER.info(STARTING + ansi().fg(RED).bold().a(RESTHEART).reset().toString() + INSTANCE + ansi().fg(RED).bold().a(instanceName).reset().toString());
        LOGGER.info(VERSION, Configuration.VERSION);
        LOGGER.debug("Configuration:\n" + configuration.toString());
    }

    /**
     * logLoggingConfiguration
     *
     * @param fork
     */
    public static void logLoggingConfiguration(Configuration configuration, boolean fork) {
        var logbackConfigurationFile = System.getProperty("logback.configurationFile");

        var usesLogback = logbackConfigurationFile != null && !logbackConfigurationFile.isEmpty();

        if (usesLogback) {
            return;
        }

        if (configuration.isLogToFile()) {
            LOGGER.info("Logging to file {} with level {}", configuration.getLogFilePath(), configuration.getLogLevel());
        }

        if (!fork) {
            if (!configuration.isLogToConsole()) {
                LOGGER.info("Stop logging to console ");
            } else {
                LOGGER.info("Logging to console with level {}", configuration.getLogLevel());
            }
        }
    }
}
