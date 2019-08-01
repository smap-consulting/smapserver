import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.model.SurveyInstance;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.CustomReportsManager;
import org.smap.sdal.managers.MessagingManagerApply;
import org.smap.sdal.managers.NotificationManager;
import org.smap.sdal.managers.ServerManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TableDataManager;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.TableColumn;
import org.smap.server.entities.HostUnreachableException;
import org.smap.server.entities.MissingSurveyException;
import org.smap.server.entities.MissingTemplateException;
import org.smap.server.entities.SubscriberEvent;
import org.smap.server.entities.UploadEvent;
import org.smap.subscribers.SmapForward;
import org.smap.subscribers.Subscriber;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import JdbcManagers.JdbcUploadEventManager;

/*****************************************************************************
 * 
 * This file is part of SMAP.
 * 
 * SMAP is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * SMAP is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * SMAP. If not, see <http://www.gnu.org/licenses/>.
 * 
 ******************************************************************************/

public class MessageProcessor {

	String confFilePath;

	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	DocumentBuilder db = null;
	Document xmlConf = null;
	Connection sd = null;
	Connection cResults;

	private static Logger log = Logger.getLogger(Subscriber.class.getName());

	private class MessageLoop implements Runnable {
		Connection sd;
		Connection cResults;
		String serverName;
		String basePath;

		public MessageLoop(Connection sd, Connection cResults, String basePath) {
			this.sd = sd;
			this.cResults = cResults;
			this.basePath = basePath;
		}

		public void run() {

			int delaySecs = 10;
			while (true) {
				System.out.print("m");
				
				try {
					// Make sure we have a connection to the database
					getDatabaseConnection();
					
					// Apply messages
					MessagingManagerApply mma = new MessagingManagerApply();
					mma.applyOutbound(sd, cResults, serverName, basePath);
					
				} catch (Exception e) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
				
				// Sleep and then go again
				try {
					Thread.sleep(delaySecs * 1000);
				} catch (Exception e) {
					// ignore
				}

			}
		}
		
		private void getDatabaseConnection() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
			// Get the connection details for the meta data database
			String dbClassMeta = null;
			String databaseMeta = null;
			String userMeta = null;
			String passwordMeta = null;

			String database = null;
			String user = null;
			String password = null;
			
			// Return if we already have a connection
			if(sd != null && cResults != null && sd.isValid(1) && cResults.isValid(1)) {
				return;
			}
			
			// Make sure any existing connections are closed
			if(sd != null) {
				log.info("Messaging: Closing sd connection");
				try {
					sd.close();
				} catch (Exception e) {
					
				}
			}
			
			if(cResults != null) {
				try {
					log.info("Messaging: Closing cResults connection");
					cResults.close();
				} catch (Exception e) {
					
				}
			}
			
			// Get the database connection
			log.info("Messaging: Getting database connections");
			
			db = dbf.newDocumentBuilder();
			xmlConf = db.parse(new File(confFilePath + "/metaDataModel.xml"));
			dbClassMeta = xmlConf.getElementsByTagName("dbclass").item(0).getTextContent();
			databaseMeta = xmlConf.getElementsByTagName("database").item(0).getTextContent();
			userMeta = xmlConf.getElementsByTagName("user").item(0).getTextContent();
			passwordMeta = xmlConf.getElementsByTagName("password").item(0).getTextContent();

			// Get the connection details for the target results database
			xmlConf = db.parse(new File(confFilePath + "/results_db.xml"));
			database = xmlConf.getElementsByTagName("database").item(0).getTextContent();
			user = xmlConf.getElementsByTagName("user").item(0).getTextContent();
			password = xmlConf.getElementsByTagName("password").item(0).getTextContent();

			Class.forName(dbClassMeta);
			sd = DriverManager.getConnection(databaseMeta, userMeta, passwordMeta);
			cResults = DriverManager.getConnection(database, user, password);
			
			serverName = GeneralUtilityMethods.getSubmissionServer(sd);
			
		}

	}

	/**
	 * @param args
	 */
	public void go(String smapId, String basePath) {

		confFilePath = "./" + smapId;

		try {
			
			// Send any pending messages
			File pFile = new File("/smap_bin/resources/properties/aws.properties");
			if (pFile.exists()) {
				Thread t = new Thread(new MessageLoop(sd, cResults, basePath));
				t.start();
			} else {
				// No message!
				// log.info("Skipping device messages. No aws properties file at:
				// /smap_bin/resources/properties/aws.properties");
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

			/*
			 * Do not close connections!  This processor is suppossed to run forever
			 */

		}

	}
	
}
