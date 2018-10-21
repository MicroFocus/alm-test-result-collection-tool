/*
 *     Copyright 2017 EntIT Software LLC, a Micro Focus company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */


package com.microfocus.mqm.atrf.core.configuration;

import com.microfocus.mqm.atrf.core.rest.RestConnector;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;


public class CliParser {

    static final Logger logger = LogManager.getLogger();

    private static final String CMD_LINE_SYNTAX = "java -jar alm-test-result-collection-tool.jar [OPTIONS]... \n";
    private static final String HEADER = "Micro Focus ALM Test Result Collection Tool";
    private static final String FOOTER = "";
    private static final String VERSION = "1.0.7";

    public static final String DEFAULT_CONF_FILE = "conf.xml";
    public static final String DEFAULT_OUTPUT_FILE = "output.xml";

    public static final String HELP_OPTION = "h";
    public static final String HELP_OPTION_LONG = "help";
    public static final String VERSION_OPTION = "v";
    public static final String VERSION_OPTION_LONG = "version";
    public static final String OUTPUT_FILE_OPTION = "o";
    public static final String OUTPUT_FILE_OPTION_LONG = "output-file";
    public static final String SOURCE_FILE_OPTION = "sf";
    public static final String SOURCE_FILE_OPTION_LONG = "source-file";
    public static final String CONFIG_FILE_OPTION = "c";
    public static final String CONFIG_FILE_OPTION_LONG = "config-file";
    public static final String PASSWORD_ALM_OPTION = "pa";
    public static final String PASSWORD_ALM_OPTION_LONG = "password-alm";
    public static final String PASSWORD_ALM_FILE_OPTION = "paf";
    public static final String PASSWORD_ALM_FILE_OPTION_LONG = "password-alm-file";
    public static final String PASSWORD_OCTANE_OPTION = "po";
    public static final String PASSWORD_OCTANE_OPTION_LONG = "password-oct";
    public static final String PASSWORD_OCTANE_FILE_OPTION = "pof";
    public static final String PASSWORD_OCTANE_FILE_OPTION_LONG = "password-oct-file";
    public static final String RUN_FILTER_ID_OPTION = "rfid";
    public static final String RUN_FILTER_ID_OPTION_LONG = "run-filter-id";
    public static final String RUN_FILTER_DATE_OPTION = "rfd";
    public static final String RUN_FILTER_DATE_OPTION_LONG = "run-filter-date";
    public static final String RUN_FILTER_LIMIT_OPTION = "rfl";
    public static final String RUN_FILTER_LIMIT_OPTION_LONG = "run-filter-limit";

    private Options options = new Options();
    private LinkedList<String> argsWithSingleOccurrence = new LinkedList<>();


    public CliParser() {
        options.addOption(Option.builder(HELP_OPTION).longOpt(HELP_OPTION_LONG).desc("Show this help").build());
        options.addOption(Option.builder(VERSION_OPTION).longOpt(VERSION_OPTION_LONG).desc("Show version of this tool").build());

        options.addOption(Option.builder(OUTPUT_FILE_OPTION).longOpt(OUTPUT_FILE_OPTION_LONG).desc("Write output to file instead of sending it to ALM Octane. File path is optional. Default file name is '" +
                DEFAULT_OUTPUT_FILE + "'." + System.lineSeparator() + " When saving to a file, the tool saves first 1000 runs." + System.lineSeparator() +
                "No ALM Octane URL or authentication configuration is required if you use this option.").hasArg().argName("FILE").optionalArg(true).build());
        options.addOption(Option.builder(CONFIG_FILE_OPTION).longOpt(CONFIG_FILE_OPTION_LONG).desc("Configuration file location. Default configuration file name is '" + DEFAULT_CONF_FILE + "'").hasArg().argName("FILE").build());
        options.addOption(Option.builder(SOURCE_FILE_OPTION).longOpt(SOURCE_FILE_OPTION_LONG).desc("If used, data is taken from source file and not from ALM").hasArg().argName("FILE").build());

        OptionGroup passAlmGroup = new OptionGroup();
        passAlmGroup.addOption(Option.builder(PASSWORD_ALM_OPTION).longOpt(PASSWORD_ALM_OPTION_LONG).desc("Password for ALM user to use for retrieving test results").hasArg().argName("PASSWORD").build());
        passAlmGroup.addOption(Option.builder(PASSWORD_ALM_FILE_OPTION).longOpt(PASSWORD_ALM_FILE_OPTION_LONG).desc("Location of file with password for ALM user").hasArg().argName("FILE").build());
        options.addOptionGroup(passAlmGroup);

        OptionGroup passOctaneGroup = new OptionGroup();
        passOctaneGroup.addOption(Option.builder(PASSWORD_OCTANE_OPTION).longOpt(PASSWORD_OCTANE_OPTION_LONG).desc("Password for ALM Octane user").hasArg().argName("PASSWORD").optionalArg(true).build());
        passOctaneGroup.addOption(Option.builder(PASSWORD_OCTANE_FILE_OPTION).longOpt(PASSWORD_OCTANE_FILE_OPTION_LONG).desc("Location of file with password for ALM Octane user").hasArg().argName("FILE").build());
        options.addOptionGroup(passOctaneGroup);

        options.addOption(Option.builder(RUN_FILTER_ID_OPTION).longOpt(RUN_FILTER_ID_OPTION_LONG).desc("Filter the ALM test results to retrieve only test runs with this run ID or higher").hasArg().argName("ID").build());
        options.addOption(Option.builder(RUN_FILTER_DATE_OPTION).longOpt(RUN_FILTER_DATE_OPTION_LONG).desc("Filter the ALM test results to retrieve only test runs from this date or later").hasArg().argName("YYYY-MM-DD").build());

        options.addOption(Option.builder(RUN_FILTER_LIMIT_OPTION).longOpt(RUN_FILTER_LIMIT_OPTION_LONG).desc("Limit number of ALM runs to retrieve ").hasArg().argName("NUMBER").build());

        argsWithSingleOccurrence.addAll(Arrays.asList(OUTPUT_FILE_OPTION, CONFIG_FILE_OPTION, PASSWORD_ALM_OPTION, PASSWORD_ALM_FILE_OPTION, PASSWORD_OCTANE_OPTION,
                PASSWORD_OCTANE_FILE_OPTION, RUN_FILTER_ID_OPTION, RUN_FILTER_DATE_OPTION, RUN_FILTER_LIMIT_OPTION));

    }

    public FetchConfiguration parse(String[] args) {
        FetchConfiguration configuration = null;
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            validateArguments(cmd);


            // load config
            String configFile = null;
            if (cmd.hasOption(CONFIG_FILE_OPTION)) {
                configFile = cmd.getOptionValue(CONFIG_FILE_OPTION);
            }
            if (StringUtils.isEmpty(configFile)) {
                configFile = DEFAULT_CONF_FILE;
            }
            try {
                File f = new File(configFile);
                logger.info("Loading configuration from : " + f.getAbsolutePath());
                configuration = FetchConfiguration.loadPropertiesFromFile(configFile);
            } catch (Exception e) {
                logger.error("Failed to load configuration file : " + e.getMessage());
                System.exit(ReturnCode.FAILURE.getReturnCode());
            }

            if(cmd.hasOption(SOURCE_FILE_OPTION)){
                String sourceFilePath = cmd.getOptionValue(SOURCE_FILE_OPTION);
                    File file = new File(sourceFilePath);
                    if (!file.exists()) {
                        logger.error("Source file does not exist : " + sourceFilePath);
                        System.exit(ReturnCode.FAILURE.getReturnCode());
                    } else if (!file.isFile()) {
                        logger.error("Invalid path to source file : " + sourceFilePath);
                        System.exit(ReturnCode.FAILURE.getReturnCode());
                    } else if (!file.canRead()) {
                        logger.error("Can not read the source file: " + sourceFilePath);
                        System.exit(ReturnCode.FAILURE.getReturnCode());
                    }
                configuration.setSourceFile(sourceFilePath);
            }

            //load output file
            if (cmd.hasOption(OUTPUT_FILE_OPTION)) {
                String outputFilePath = cmd.getOptionValue(OUTPUT_FILE_OPTION);
                if (StringUtils.isEmpty(outputFilePath)) {
                    outputFilePath = DEFAULT_OUTPUT_FILE;
                }
                configuration.setOutputFile(outputFilePath);
                File outputFile = new File(outputFilePath);

                if (!outputFile.exists()) {
                    boolean canCreate = true;
                    String errorMsg = null;
                    try {
                        if (!outputFile.createNewFile()) {
                            canCreate = false;
                        }
                    } catch (IOException e) {

                        //check if parent exist
                        Path parent = Paths.get(outputFile.getParent());
                        if (!parent.toFile().exists()) {
                            logger.error(String.format("Can not create the output file '%s' as parent folder '%s' is not exist", outputFile.getAbsolutePath(), outputFile.getParent()));
                            System.exit(ReturnCode.FAILURE.getReturnCode());
                        }

                        //else some other issue
                        canCreate = false;
                        errorMsg = " : " + e.getMessage();
                    }
                    if (!canCreate) {
                        logger.error("Can not create the output file  '" + outputFile.getAbsolutePath() + "'" + errorMsg);
                        System.exit(ReturnCode.FAILURE.getReturnCode());
                    }
                }
                if (!outputFile.canWrite()) {
                    logger.error("Can not write to the output file: " + outputFile.getAbsolutePath());
                    System.exit(ReturnCode.FAILURE.getReturnCode());
                }
                logger.info("Output results to file  : " + outputFile.getAbsolutePath());
            }

            //load alm password
            if (cmd.hasOption(PASSWORD_ALM_OPTION)) {
                configuration.setAlmPassword(cmd.getOptionValue(PASSWORD_ALM_OPTION));
            } else if (cmd.hasOption(PASSWORD_ALM_FILE_OPTION)) {
                configuration.setAlmPassword(getPasswordFromFile(cmd.getOptionValue(PASSWORD_ALM_FILE_OPTION)));
            }

            //load octane password
            if (cmd.hasOption(PASSWORD_OCTANE_OPTION)) {
                configuration.setOctanePassword(cmd.getOptionValue(PASSWORD_OCTANE_OPTION));
            } else if (cmd.hasOption(PASSWORD_OCTANE_FILE_OPTION)) {
                configuration.setOctanePassword(getPasswordFromFile(cmd.getOptionValue(PASSWORD_OCTANE_FILE_OPTION)));
            }

            //run filter options
            if (cmd.hasOption(RUN_FILTER_ID_OPTION)) {
                configuration.setAlmRunFilterStartFromId(cmd.getOptionValue(RUN_FILTER_ID_OPTION));
            }
            if (cmd.hasOption(RUN_FILTER_DATE_OPTION)) {
                configuration.setAlmRunFilterStartFromDate(cmd.getOptionValue(RUN_FILTER_DATE_OPTION));
            }
            if (cmd.hasOption(RUN_FILTER_LIMIT_OPTION)) {
                configuration.setRunFilterFetchLimit(cmd.getOptionValue(RUN_FILTER_LIMIT_OPTION));
            }

            try {
                configuration.validateProperties();
            } catch (Exception e) {
                logger.error("Failed to parse configuration file : " + e.getMessage());
                System.exit(ReturnCode.FAILURE.getReturnCode());
            }

            initProxyIfDefined(configuration);
            configuration.logProperties();

        } catch (Exception e) {
            logger.error(e.getMessage());
            System.exit(ReturnCode.FAILURE.getReturnCode());
        }
        return configuration;
    }

    private String getPasswordFromFile(String fileName) {
        File file = new File(fileName);

        if (!file.exists()) {
            logger.error("Password file does not exist : " + fileName);
            System.exit(ReturnCode.FAILURE.getReturnCode());
        } else if (!file.isFile()) {
            logger.error("Invalid path to password file : " + fileName);
            System.exit(ReturnCode.FAILURE.getReturnCode());
        } else if (!file.canRead()) {
            logger.error("Can not read the password file: " + fileName);
            System.exit(ReturnCode.FAILURE.getReturnCode());
        } else if (file.length() > 256) {
            logger.error("Password file is too big (>256b): " + fileName);
            System.exit(ReturnCode.FAILURE.getReturnCode());
        }

        String password = null;
        try {
            password = FileUtils.readFileToString(new File(fileName));
        } catch (IOException e) {
            logger.error("Can not read the password file: " + fileName);
            System.exit(ReturnCode.FAILURE.getReturnCode());
        }
        return password;
    }

    private void validateArguments(CommandLine cmd) {
        //validation before loading other args
        for (String arg : argsWithSingleOccurrence) {
            if (cmd.getOptionProperties(arg).size() > 1) {
                logger.error("Only single occurrence is allowed for argument: '" + arg + "'");
                System.exit(ReturnCode.FAILURE.getReturnCode());
            }
        }
    }

    private void printVersion() {
        System.out.println(HEADER);
        System.out.println("Version: " + VERSION);
        System.out.println(FOOTER);
    }

    private void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(CMD_LINE_SYNTAX, HEADER, options, FOOTER);
    }

    private void initProxyIfDefined(FetchConfiguration configuration) {
        if (StringUtils.isNotEmpty(configuration.getProxyHost()) && StringUtils.isNotEmpty(configuration.getProxyPort())) {
            try {
                logger.info("Setting proxy " + configuration.getProxyHost() + ":" + configuration.getProxyPort());
                int port = Integer.parseInt(configuration.getProxyPort());
                RestConnector.setProxy(configuration.getProxyHost(), port);
            } catch (Exception e) {
                logger.error("Failed to set proxy : " + e.getMessage());
                System.exit(ReturnCode.FAILURE.getReturnCode());
            }


        }
    }


    public void handleHelpAndVersionOptions(String[] args) {

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.print(e.getMessage());
            System.exit(ReturnCode.FAILURE.getReturnCode());
        }

        //help
        if (cmd.hasOption(HELP_OPTION)) {
            printHelp();
            System.exit(ReturnCode.SUCCESS.getReturnCode());
        }

        //version
        if (cmd.hasOption(VERSION_OPTION)) {
            printVersion();
            System.exit(ReturnCode.SUCCESS.getReturnCode());
        }
    }
}
