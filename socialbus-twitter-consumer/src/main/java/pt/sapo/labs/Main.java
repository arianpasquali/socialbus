package pt.sapo.labs;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import org.apache.commons.cli.*;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.TwitterException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Properties;

public class Main{

	private static Logger logger = LoggerFactory.getLogger(Main.class);
	
	private static final String INPUT_FILE = "logback-smtp-client.xml";
    private static final String APP_VERSION = loadVersion();
	
	private static void startLogger() throws JoranException{
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(context);
		context.reset();
		
		InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(INPUT_FILE);
		if(inputStream == null) throw new IllegalStateException("File not found: " + INPUT_FILE);
		configurator.doConfigure(inputStream);

		
		StatusPrinter.printInCaseOfErrorsOrWarnings(context);		
	}


    public static void startApp(String configFilePath,
                                String filterFilePath,
                                String oauthFilePath) {

        logger.info("Starting application...");


        try {

            AbstractConfiguration.setDefaultListDelimiter(';');

            logger.debug("loading config file : " + configFilePath);
            logger.debug("loading oauth token file : " + oauthFilePath);

            PropertiesConfiguration clientConfig = new PropertiesConfiguration(configFilePath);
            PropertiesConfiguration oauthConfig = new PropertiesConfiguration(oauthFilePath);

            CompositeConfiguration config = new CompositeConfiguration();

            oauthConfig.setListDelimiter(';');

            config.addProperty("agent.version",APP_VERSION);
            config.addProperty("config.file.path", configFilePath);
            config.addProperty("oauth.file.path",oauthFilePath);

            if(filterFilePath != null && filterFilePath != ""){

                config.addProperty("filter.file.path",filterFilePath);

                PropertiesConfiguration filterConfig = new PropertiesConfiguration(filterFilePath);
                config.addConfiguration(filterConfig);
            }

            config.addConfiguration(clientConfig);
            //config.addConfiguration(oauthConfig);

//          Print all properties
            Iterator<String> keys = config.getKeys();

            logger.info("[configuration] ------------------");
            while ( keys.hasNext() ){
                String configKey = keys.next();

                logger.info(configKey + ": " + config.getProperty(configKey));
            }
            logger.info("----------------------------------");

            IApp app = new TwitterStreamClientApplication(config);
            ShutdownInterceptor shutdownInterceptor = new ShutdownInterceptor(app);
            Runtime.getRuntime().addShutdownHook(shutdownInterceptor);

            logger.info("Initializing application...");
            app.start();

        } catch (ConfigurationException e) {
            logger.error("Unable to read config file" ,e);
        }
    }
	/**
	 * Main entry of this application.
	 */
	public static void main(String[] args)throws TwitterException,JoranException {

        startLogger();

        logger.info("************************************************************");
        logger.info("Version : " + APP_VERSION);
        logger.info("************************************************************");

        Option basicConfigFile = OptionBuilder.withArgName("config")
                .hasArg()
                .withDescription(  "basic configuration file" )
                .create( "config" );

        Option oauthConfigFile = OptionBuilder.withArgName("oauth")
                .hasArg()
                .withDescription(  "oauth configuration file" )
                .create( "oauth" );

        Option filterConfigFile = OptionBuilder.withArgName("filter")
                .hasArg()
                .withDescription("filter streams configuration file")
                .create("filter")
                ;

        filterConfigFile.setRequired(false);

        // create Options object
        Options options = new Options();

        // add t option
        options.addOption(basicConfigFile);
        options.addOption(oauthConfigFile);
        options.addOption(filterConfigFile);

        CommandLineParser parser = new GnuParser();
        try {
            // parse the command line arguments
             CommandLine line = parser.parse( options, args );


            logger.info(String.valueOf(args));
            for (String arg : args){
                logger.info(arg);
            }


            // validate that block-size has been set
            if( !line.hasOption( "config" ) || !line.hasOption( "oauth" )) {
                // automatically generate the help statement
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( " ", options );

                System.exit(0);
            }

            loadConfigFiles(line);
        }
        catch( ParseException exp ) {
            // oops, something went wrong
            //logger.error("Parsing failed.  Reason: ", exp);
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
        }
	}

    private static void loadConfigFiles(CommandLine line) {
        logger.info("Loading configuration files...");

        startApp(line.getOptionValue("config"),
                line.getOptionValue("filter"),
                line.getOptionValue("oauth"));
    }

    private static String loadVersion() {
        String userAgent = "socialbus-Twitter-Consumer";
        try {
            InputStream stream = Main.class.getClassLoader().getResourceAsStream("build.properties");
            try {
                Properties prop = new Properties();
                prop.load(stream);

                String version = prop.getProperty("version");
                userAgent += " " + version;
            } finally {
                stream.close();
            }
        } catch (IOException ex) {
            logger.error("fail to load build properties info",ex);
            // ignore
        }
        return userAgent;
    }
}