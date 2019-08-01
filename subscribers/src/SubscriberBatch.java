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
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.MessagingManager;
import org.smap.sdal.managers.MessagingManagerApply;
import org.smap.sdal.managers.NotificationManager;
import org.smap.sdal.managers.ServerManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TableDataManager;
import org.smap.sdal.managers.TaskManager;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Notification;
import org.smap.sdal.model.NotifyDetails;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.model.ServerData;
import org.smap.sdal.model.SubmissionMessage;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import JdbcManagers.JdbcUploadEventManager;

/*****************************************************************************

This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

 ******************************************************************************/

public class SubscriberBatch {

	String confFilePath;

	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	DocumentBuilder db = null;
	Document xmlConf = null;		
	Connection sd = null;
	Connection cResults;

	private static Logger log =
			Logger.getLogger(Subscriber.class.getName());

	private static LogManager lm = new LogManager();		// Application log

	/**
	 * @param args
	 */
	public void go(String smapId, String basePath, String subscriberType) {

		confFilePath = "./" + smapId;

		// Get the connection details for the meta data database
		String dbClassMeta = null;
		String databaseMeta = null;
		String userMeta = null;
		String passwordMeta = null;

		String database = null;
		String user = null;
		String password = null;
		JdbcUploadEventManager uem = null;
		
		Survey sdalSurvey = null;

		String sqlUpdateStatus = "insert into subscriber_event ("
				+ "se_id,"
				+ "ue_id,"
				+ "subscriber,"
				+ "status,"
				+ "reason,"
				+ "dest) "
				+ "values (nextval('se_seq'), ?, ?, ?, ?, ?)";
		PreparedStatement pstmt = null;
		
		String sqlResultsDB = "update upload_event set results_db_applied = 'true' where ue_id = ?";
		PreparedStatement pstmtResultsDB = null;
		String serverName = null;

		String language = "none";
		try {
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

			uem = new JdbcUploadEventManager(sd);
			pstmt = sd.prepareStatement(sqlUpdateStatus);
			pstmtResultsDB = sd.prepareStatement(sqlResultsDB);

			// Default to english though we could get the locales from a server level setting
			Locale locale = new Locale("en");
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			serverName = GeneralUtilityMethods.getSubmissionServer(sd);

			/*
			 * Get subscribers and their configuration
			 * This is re-evaluated every time the batch job is run to allow
			 * configurations to be updated and applied immediately
			 * However it may be better to add an external trigger that forces the
			 * configuration files to be re-read when directed by the administrator
			 */
			List<Subscriber> subscribers = null;
			if(subscriberType.equals("upload")) {
				subscribers = init(sd);		// Get subscribers 
			} else if(subscriberType.equals("forward")) {
				subscribers = initForward(sd, localisation);		// Get subscribers 
			} else {
				log.info("Unknown subscriber type: " + subscriberType + " known values are upload, forward");
			}

			Date timeNow = new Date();
			String tz = "UTC";
			if(subscribers != null && !subscribers.isEmpty()) {

				/*
				 * Loop through each subscriber and then 
				 *  for enabled subscribers
				 *  process all results that match the subscriber filter
				 */
				for(Subscriber s : subscribers) {

					if(s.isEnabled()) {

						List<UploadEvent> uel = null;

						if(subscriberType.equals("upload")) {
							uel = uem.getFailed(s.getSubscriberName());		// Get pending jobs
						} else if(subscriberType.equals("forward")) {
							uel = uem.getFailedForward(s.getSubscriberName(), s.getSurveyId());		// Get pending jobs 
						} 

						if(uel.isEmpty()) {

							System.out.print(".");

						} else {
							log.info("\nUploading subscriber: " + s.getSubscriberName() + " : " + timeNow.toString());

							for(UploadEvent ue : uel) {
								log.info("        Survey:" + ue.getSurveyName() + ":" + ue.getId());

								SurveyInstance instance = null;
								SubscriberEvent se = new SubscriberEvent();
								se.setSubscriber(s.getSubscriberName());
								se.setDest(s.getDest());
								String uploadFile = ue.getFilePath();

								log.info("Upload file: " + uploadFile);
								InputStream is = null;
								InputStream is2 = null;
								InputStream is3 = null;

								try {
									int oId = GeneralUtilityMethods.getOrganisationIdForSurvey(sd, ue.getSurveyId());
									Organisation organisation = GeneralUtilityMethods.getOrganisation(sd, oId);
									Locale orgLocale = new Locale(organisation.locale);
									ResourceBundle orgLocalisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", orgLocale);

									// Get the submitted results as an XML document
									is = new FileInputStream(uploadFile);

									DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
									dbf.setNamespaceAware(true);
									DocumentBuilder b = dbf.newDocumentBuilder();							
									Document surveyDocument = b.parse(is);

									// Get an XPath object to parse the results
									XPathFactory factory = XPathFactory.newInstance();
									XPath xpath = factory.newXPath();

									// Process the results if the filter xpath expression returns true
									boolean process = true;
									if(s.getSubscriberFilter() != null) {
										XPathExpression expr = xpath.compile(s.getSubscriberFilter());
										Boolean result = (Boolean) expr.evaluate(surveyDocument, XPathConstants.BOOLEAN);
										process=result.booleanValue();
									}

									if(process) {

										is2 = new FileInputStream(uploadFile);

										// Convert the file into a survey instance object
										instance = new SurveyInstance(is2);
										log.info("UUID:" + instance.getUuid());

										//instance.getTopElement().printIEModel("   ");	// Debug 

										// Get the template for this survey
										String templateName = instance.getTemplateName();
										SurveyTemplate template = new SurveyTemplate(orgLocalisation);

										SurveyManager sm = new SurveyManager(localisation, "UTC");
										sdalSurvey = sm.getSurveyId(sd, templateName);	// Get the survey from the templateName / ident
										
										template.readDatabase(sd, templateName, false);					
										template.extendInstance(sd, instance, true, sdalSurvey);	// Extend the instance with information from the template
										// instance.getTopElement().printIEModel("   ");	// Debug

										// Get attachments from incomplete submissions
										getAttachmentsFromIncompleteSurveys(sd, s.getSubscriberName(), ue.getFilePath(), ue.getOrigSurveyIdent(), ue.getIdent());

										is3 = new FileInputStream(uploadFile);	// Get an input stream for the file in case the subscriber uses that rather than an Instance object
										s.upload(instance, 
												is3, 
												ue.getUserName(), 
												ue.getServerName(), 
												ue.getImei(), 
												se,
												confFilePath, 
												ue.getFormStatus(),
												basePath, 
												uploadFile, 
												ue.getUpdateId(),
												ue.getId(),
												ue.getUploadTime(),
												ue.getSurveyNotes(),
												ue.getLocationTrigger(),
												ue.getAuditFilePath(),
												orgLocalisation, 
												sdalSurvey);	// Call the subscriber	

									} else {

										log.info("        filtered");
										se.setStatus("filtered");
										se.setReason(s.getSubscriberFilter());

									}

								} catch (FileNotFoundException e) {

									se.setStatus("error");
									se.setReason("Submission File Not Found:" + uploadFile);

								} catch (MissingSurveyException e) {

									se.setStatus("error");
									se.setReason("Results file did not specify a survey template:" + uploadFile);

								} catch (MissingTemplateException e) {

									se.setStatus("error");
									se.setReason("No template named: " + e.getMessage() + " in database");

								} catch (HostUnreachableException e) {

									se.setStatus("host_unreachable");
									se.setReason(e.getMessage());

								} catch (Exception e) {

									e.printStackTrace();
									se.setStatus("error");
									se.setReason(e.getMessage());

								} finally {

									try {
										if(is != null) {is.close();}			
										if(is2 != null) {is2.close();}				
										if(is3 != null) {is3.close();}
									} catch (Exception e) {

									}

									// Save the status unless the host was unreachable
									//  unreachable events are logged but not otherwise recorded
									if(se.getStatus() != null && !se.getStatus().equals("host_unreachable")) {

										pstmt.setInt(1, ue.getId());
										pstmt.setString(2, se.getSubscriber());
										pstmt.setString(3, se.getStatus());
										pstmt.setString(4, se.getReason());
										pstmt.setString(5, se.getDest());
										pstmt.executeUpdate();
										
										// Add a flag in the for results db updates in the upload_event table to improve performance
										if(s.getSubscriberName().equals("results_db")) {
											pstmtResultsDB.setInt(1, ue.getId());
											pstmtResultsDB.executeUpdate();
										}

									} else if(se.getStatus() != null && se.getStatus().equals("host_unreachable")) {
										// If the host is unreachable then stop forwarding for 10 seconds
										// Also stop processing this subscriber, it may be that it has been taken off line

										int forwardSleep = 60;
										Date now = new Date();
										String dt = DateFormat.getDateTimeInstance().format(now);
										log.info("No connectivity: " + dt);
										try {
											Thread.sleep(forwardSleep * 1000);
										} catch (Exception e) {
											// ignore
										}
									}
								}	

								// If the host is unreachable stop processing this subscriber, it may be that it has been taken off line
								if(se.getStatus() != null && se.getStatus().equals("host_unreachable")) {
									log.info("Stopping processing of subscriber: " + s.getSubscriberName());
									break;
								}
							}
						}
					} 
				}
			} else {
				System.out.print("#");
			}

			/*
			 * Apply any other subscriber type dependent processing
			 */
			if(subscriberType.equals("upload")) {
				applyReminderNotifications(sd, cResults, basePath, serverName);
			} else if(subscriberType.equals("forward")) {
				// Erase any templates that were deleted more than a set time ago
				eraseOldTemplates(sd, cResults, localisation, basePath);

				// Apply synchronisation
				// 1. Get all synchronisation notifications
				// 2. Loop through each prikey not in sync table 
				// 2.a  Synchronise
				// 2.b  Update sync table

				if(GeneralUtilityMethods.documentSyncEnabled(sd)) {
					boolean haveSyncNotifications = false;
					String urlprefix = "https://" + serverName + "/";	// Need to get server name for image processing
					HashMap<String, String> docServerConfig = GeneralUtilityMethods.docServerConfig(sd);

					String sqlNot = "select id, s_id, notify_details from forward where enabled = 'true' and target = 'document'";
					PreparedStatement pstmtNot = sd.prepareStatement(sqlNot);

					String sqlMarkDone = "insert into sync (s_id, n_id, prikey) values(?, ?, ?)";
					PreparedStatement pstmtMarkDone = cResults.prepareStatement(sqlMarkDone);
					
					PreparedStatement pstmtRecord = null;
					PreparedStatement pstmtCheckNeed = null;

					try {
						ResultSet rs = pstmtNot.executeQuery();
						TableDataManager tdm = new TableDataManager(localisation, tz);

						while(rs.next()) {
							haveSyncNotifications = true;
							int nId = rs.getInt(1);
							int sId = rs.getInt(2);
							String details = rs.getString(3);

							Form topForm = GeneralUtilityMethods.getTopLevelForm(sd, sId);

							if(GeneralUtilityMethods.tableExists(cResults, topForm.tableName)) {

								// Get the records that need synchronising
								String prikeyFilter = "prikey not in (select prikey from sync where s_id = " + 
										sId + " and n_id = " + nId + ")";	
								
								// Confirm we need to do this synchronisation
								boolean syncRequired = false;
								String sqlCheckNeed = "select count(*) from " + topForm.tableName + " where " + prikeyFilter;
								pstmtCheckNeed = cResults.prepareStatement(sqlCheckNeed);
								try {
									ResultSet rsCN = pstmtCheckNeed.executeQuery();
									if(rsCN.next()) {
										if(rsCN.getInt(1) > 0) {
											syncRequired = true;
										}
									}
								} catch(Exception e) {
									if(e.getMessage() != null && e.getMessage().contains("does not exist")) {
										// Ignore missing table it will presumably be added when there is data
									} else {
										log.log(Level.SEVERE, e.getMessage(), e);
									}
								}
								
								if(syncRequired) {
									log.info("Synchronising notification " +nId + " on " + topForm.tableName);
									JSONArray ja = null;
	
									boolean getParkey = false;	// For top level form TODO loop through forms
									boolean mgmt = false;		// TODO get from notification
									int managedId = 0;			// TODO get from notification
									String surveyIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
									ArrayList<TableColumn> columns = GeneralUtilityMethods.getColumnsInForm(
											sd,
											cResults,
											localisation,
											language,
											sId,
											surveyIdent,
											null,		// No need for user - we are super user
											null,		// Roles to apply
											topForm.parentform,
											topForm.id,
											topForm.tableName,
											false,		// Don't include read only
											getParkey,	// Include parent key if the form is not the top level form (fId is 0)
											false,		// Don't include bad columns
											true,		// include instance id
											true,		// Include prikey
											true,		// include other meta data
											true,		// include preloads
											true,		// include instancename
											true,		// include survey duration
											true,		// Super user
											false,		// Don't include HXL
											true	,		// include audit data
											tz,
											false		// mgmt
											);
	
									if(mgmt) {
										CustomReportsManager crm = new CustomReportsManager ();
										ReportConfig config = crm.get(sd, managedId, -1);
										columns.addAll(config.columns);
									}
	
									pstmt = tdm.getPreparedStatement(
											sd, 
											cResults,
											columns,
											urlprefix,
											sId,
											topForm.tableName,
											0,				// parkey ??
											null,			// Not searching on HRK
											null,			// No user ident, we are super user
											null,			// No list of roles
											null,			// No specific sort column
											null,			// No specific sort direction
											mgmt,
											false,			// No grouping
											false,			// Not data tables
											0,				// Start from zero
											getParkey,
											0,	// Start from the beginning of the parent key
											true,			// Super User
											false,			// Return records greater than or equal to primary key
											"none",			// Do not return bad records
											prikeyFilter,
											null	,			// key filter
											tz,
											null	,			// instance id
											null	,			// advanced filter
											null,			// Date filter name
											null,			// Start date
											null				// End date
											);
	
									// Set parameters for custom filter
	
									if(pstmt != null) {
										log.info("Get sync records: " + pstmt.toString());
										ja = tdm.getData(
												pstmt,
												columns,
												urlprefix,
												false,		// No grouping for duplicate queries
												false,		// Boolean not data tables
												0			// No limit
												);
									}
	
									if(ja == null) {
										ja = new JSONArray();
									}
	
									// Process each record
									for(int i = 0; i < ja.length(); i++) {
										JSONObject jo = (JSONObject) ja.get(i);
										log.info("  Rec: " + ja.get(i));
	
										// 1. Add meta data to the record
										// Organisation id
	
										// 2. Send to server
										String key = serverName + "_" + sId + "_" + jo.getString("prikey");
										boolean success = putDocument("banana", "form", key, jo.toString(), 
												docServerConfig.get("server"),
												docServerConfig.get("user"),
												docServerConfig.get("password"));
	
										// 3. Mark as processed (if successful)
										if(success) {
											pstmtMarkDone.setInt(1,  sId);
											pstmtMarkDone.setInt(2,  nId);
											pstmtMarkDone.setInt(3, jo.getInt("prikey"));
											pstmtMarkDone.executeUpdate();
										} else {
											break;  // Delay before continuing
										}
									}
								}


							} else {
								log.info("=== No results in table: " + topForm.tableName);
							}

						}
						if(!haveSyncNotifications) {
							log.info("=== No enabled synchronisation notifications");
						}
					} finally {
						if(pstmtNot != null) {try {pstmtNot.close();} catch(Exception e) {}}
						if(pstmtRecord != null) {try {pstmtRecord.close();} catch(Exception e) {}}
						if(pstmtMarkDone != null) {try {pstmtMarkDone.close();} catch(Exception e) {}}
						if(pstmtCheckNeed != null) {try {pstmtCheckNeed.close();} catch(Exception e) {}}
					}
				} else {
					// log.info("=== sync not enabled");
				}


			}

			subscribers = null;

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtResultsDB != null) { pstmtResultsDB.close();}} catch (SQLException e) {}

			if(uem != null) {uem.close();}

			try {				
				if (sd != null) {
					sd.close();
					sd = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection");
				e.printStackTrace();
			}

			try {
				if (cResults != null) {
					cResults.close();
					cResults = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close results connection");
				e.printStackTrace();
			}
		}

	}

	/*
	 * Erase deleted templates more than a specified number of days old
	 */
	private void eraseOldTemplates(Connection sd, Connection cResults, ResourceBundle localisation, String basePath) {

		PreparedStatement pstmt = null;
		PreparedStatement pstmtTemp = null;
		PreparedStatement pstmtFix = null;

		try {
			
			ServerManager server = new ServerManager();
			ServerData sdata = server.getServer(sd, localisation);
			int interval = sdata.keep_erased_days;
			if(interval <= 0) {
				interval = 100;		// Default to 100
			}

			ServerManager sm = new ServerManager();
			String sql = "select s_id, "
					+ "p_id, "
					+ "last_updated_time, "
					+ "ident,"
					+ "display_name "
					+ "from survey where deleted "
					+ "and hidden = 'false' "
					+ "and (last_updated_time < now() - interval '" + interval + " days') "
					+ "order by last_updated_time;";
			pstmt = sd.prepareStatement(sql);
			
			String sqlFix = "select s_id, "
					+ "p_id, "
					+ "last_updated_time, "
					+ "ident,"
					+ "display_name "
					+ "from survey where deleted "
					+ "and hidden = 'false' "
					+ "and last_updated_time is null";
			pstmtFix = sd.prepareStatement(sqlFix);

			String sqlTemp = "update survey set last_updated_time = ? where s_id = ?";
			pstmtTemp = sd.prepareStatement(sqlTemp);

			/*
			 * Temporary fix for lack of accurate date when a survey was deleted
			 */
			ResultSet rs = pstmtFix.executeQuery();
			while(rs.next()) {
				int sId = rs.getInt("s_id");
				String deletedDate = rs.getString("last_updated_time");
				String surveyDisplayName = rs.getString("display_name");

				// Get deleted date from the display name
				int idx1 = surveyDisplayName.indexOf("(20");
				String date = null;
				if(idx1 > -1) {
					int idx2 = surveyDisplayName.lastIndexOf(")");
					if(idx2 > -1 && idx2 > idx1) {
						String d = surveyDisplayName.substring(idx1 +1, idx2);
						String [] da = d.split(" ");
						if(da.length > 0) {
							date = da[0].replaceAll("_", "-");
						}
					}
				}

				if(date == null) {
					idx1 = surveyDisplayName.lastIndexOf("_20");
					if(idx1 > -1) {
						String d = surveyDisplayName.substring(idx1 +1);
						String [] da = d.split("_");
						int year = -1;
						int month = -1;
						int day = -1;
						if(da.length > 0) {
							try {
								year = Integer.parseInt(da[0]);
								month = Integer.parseInt(da[1]);
								String [] dd = da[2].split(" ");
								day = Integer.parseInt(dd[0]);
							} catch (Exception e) {

							}
						}
						if(year > -1 && month > -1 && day > -1) {
							date = year + "-" + month + "-" + day;
						}
					}
				}
				if(date == null) {
					log.info("******** Failed to get date from: " + surveyDisplayName + " deleted date was: " + deletedDate);
				} else {
					try {
						java.sql.Date dx = java.sql.Date.valueOf(date);
						pstmtTemp.setDate(1, dx);
						pstmtTemp.setInt(2,  sId);
						pstmtTemp.executeUpdate();	
					} catch (Exception e) {
						log.log(Level.SEVERE, "Error: " + surveyDisplayName + " : " + e.getMessage());
					}
				}

			}

			/*
			 * Process surveys to be deleted for real now
			 */
			log.info("Erase interval set to: " + interval);
			log.info("Check for templates to erase: " + pstmt.toString());
			rs = pstmt.executeQuery();	
			while(rs.next()) {
				int sId = rs.getInt("s_id");
				int projectId = rs.getInt("p_id");
				String deletedDate = rs.getString("last_updated_time");
				String surveyIdent = rs.getString("ident");
				String surveyDisplayName = rs.getString("display_name");

				log.info("######### Erasing: " + surveyDisplayName + " which was deleted on " +  deletedDate);
				sm.deleteSurvey(sd, cResults, "auto erase", projectId, sId, surveyIdent, surveyDisplayName, basePath, true, "yes");
			}


		} catch (Exception e) {
			e.printStackTrace();
		} finally {			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}	
			try {if (pstmtTemp != null) {pstmtTemp.close();}} catch (SQLException e) {}
			try {if (pstmtFix != null) {pstmtFix.close();}} catch (SQLException e) {}	
		}
	}
	/*
	 * Create a Subscriber object for each subscriber
	 */
	public List<Subscriber> init(Connection connection) throws SQLException {
		File confDir = new File(confFilePath);
		List<Subscriber> subscribers = new ArrayList<Subscriber> ();

		String confPaths[]  = confDir.list();
		if(confPaths != null) {
			for(String confFile : confPaths) {
				if ((confFile.lastIndexOf("xml") == confFile.length() - 3) &&
						!confFile.equals("metaDataModel.xml")) {	// Ignore non XML files and the meta database config file
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					DocumentBuilder db = null;
					try {
						db = dbf.newDocumentBuilder();
					} catch (ParserConfigurationException e) {
						e.printStackTrace();
						return null;
					}
					Document xmlConf = null;

					String subscriberType = null;
					try {

						xmlConf = db.parse(new File(confFilePath + "/" + confFile));
						subscriberType = xmlConf.getElementsByTagName("type").item(0).getTextContent();
						Class subClass = Class.forName(subscriberType);

						// The name of the subscriber is the name of the configuration file without the .xml extension
						String subscriberName = confFile.substring(0, confFile.lastIndexOf("xml") - 1);

						Subscriber sub = (Subscriber) subClass.newInstance();		
						// Save the configuration document in the Subscriber
						sub.setConfigurationDocument(xmlConf);
						sub.setSubscriberName(subscriberName);
						subscribers.add(sub);	// Add the new subscriber to the list of subscribers


					} catch (SAXException e) {
						log.log(Level.SEVERE, "SAXException on configuration file: " + confFile);
					} catch (IOException e) {
						log.log(Level.SEVERE, "IOException on configuration file: " + confFile);
					} catch(ClassNotFoundException e) {
						log.log(Level.SEVERE, "ClassNotFoundException on configuration file: " + confFile + " with class: " + subscriberType);
					} catch (IllegalAccessException e) {
						log.log(Level.SEVERE, "IllegalAccessException on configuration file: " + confFile + " with class: " + subscriberType);
					} catch (InstantiationException e) {
						log.log(Level.SEVERE, "InstantiationException on configuration file: " + confFile + " with class: " + subscriberType);
						e.printStackTrace();
					}
				}
			}
		}
		return subscribers;
	}

	/*
	 * Create a Subscriber object for each forwarding subscriber
	 */
	public List<Subscriber> initForward(Connection connection, ResourceBundle localisation) throws SQLException {

		List<Subscriber> subscribers = new ArrayList<Subscriber> ();

		/*
		 * This type of subscriber is per link, that is 
		 * survey -> remote survey so create a subscriber object for each
		 */
		NotificationManager fm = new NotificationManager(localisation);
		ArrayList<Notification> forwards = fm.getEnabledNotifications(connection, "forward", "submission");
		for(int i = 0; i < forwards.size(); i++) {
			Notification f = forwards.get(i);
			Subscriber sub = (Subscriber) new SmapForward();		
			// Save the configuration document in the Subscriber
			int sId = f.s_id;
			String remote_sId = f.remote_s_ident;
			String remoteUrl = f.remote_host;
			int idx = remoteUrl.lastIndexOf("//");
			if(idx >= 0) {
				String host = remoteUrl.substring(idx + 2);

				sub.setEnabled(true);
				sub.setSubscriberName("fwd_" + sId + "_" + host + remote_sId + "_" + f.id);
				sub.setSurveyId(sId);	
				sub.setSurveyIdRemote(remote_sId);
				sub.setUser(f.remote_user);
				sub.setPassword(f.remote_password);
				sub.setSurveyNameRemote(f.remote_s_name);
				sub.setHostname(remoteUrl);
				subscribers.add(sub);	// Add the new subscriber to the list of subscribers
			} else {
				log.log(Level.SEVERE, "Error: Invalid host (" + remoteUrl + ") for survey " + sId);
			}

		}
		return subscribers;
	}

	/*
	 * ODK sends large attachments in separate "incomplete" posts
	 * Get these now
	 * Only do it once for all subscribers, so once the attachments from the incomplete posts have been moved
	 *  to the complete submission then the first subscriber to do this will be marked as having processed the
	 *  attachments.  All other subscribers will then ignore incomplete attachments
	 */
	private void getAttachmentsFromIncompleteSurveys(Connection connectionSD, 
			String subscriberName, 
			String finalPath, 
			String origIdent, 
			String ident) {


		String sql = "select ue.ue_id, ue.file_path from upload_event ue " +
				"where ue.status = 'success' " +
				" and ue.orig_survey_ident = ? " +
				" and ue.ident = ? " +
				" and ue.incomplete = 'true'" +
				" and not ue.results_db_applied ";
				//" and not exists (select se.se_id from subscriber_event se where se.ue_id = ue.ue_id)";

		String sqlUpdate = "insert into subscriber_event (se_id, ue_id, subscriber, status, reason) values (nextval('se_seq'), ?, ?, ?, ?);";

		PreparedStatement pstmt = null;
		PreparedStatement pstmtUpdate = null;
		try {
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, origIdent);
			pstmt.setString(2, ident);

			pstmtUpdate = sd.prepareStatement(sqlUpdate);

			File finalFile = new File(finalPath);
			File finalDirFile = finalFile.getParentFile();
			String finalDir = finalDirFile.getPath();

			log.info("Get incomplete attachments: " + pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				log.info("++++++ Processing incomplete file name is: " + rs.getString(2));

				int ue_id = rs.getInt(1);
				File sourceFile = new File(rs.getString(2));
				File sourceDirFile = sourceFile.getParentFile();

				File files[] = sourceDirFile.listFiles();
				for(int i = 0; i < files.length; i++) {
					log.info("       File: " + files[i].getName());
					String fileName = files[i].getName();
					if(!fileName.endsWith("xml")) {
						log.info("++++++ Moving " + fileName + " to " + finalDir);
						files[i].renameTo(new File(finalDir + "/" + fileName));
					}
				}
				pstmtUpdate.setInt(1, ue_id);
				pstmtUpdate.setString(2,subscriberName);
				pstmtUpdate.setString(3,"merged");
				pstmtUpdate.setString(4,"Files moved to " + finalDir);
				pstmtUpdate.executeUpdate();

			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
			if(pstmtUpdate != null) try {pstmtUpdate.close();} catch(Exception e) {};
		}

	}

	private boolean putDocument(String index, String type, String key, String doc, String host_name, String user, String password) {
		boolean success = true;

		CloseableHttpClient httpclient = null;
		ContentType ct = null;
		HttpResponse response = null;
		int responseCode = 0;
		String responseReason = null;
		int port = 9200;

		try {
			HttpHost target = new HttpHost(host_name, port, "http");
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(
					new AuthScope(target.getHostName(), target.getPort()),
					new UsernamePasswordCredentials(user, password));
			httpclient = HttpClients.custom()
					.setDefaultCredentialsProvider(credsProvider)
					.build();

			String url = "http://" + host_name + ":" + port + "/" + index + "/" + type + "/" + key;
			HttpClientContext localContext = HttpClientContext.create();
			HttpPut req = new HttpPut(URI.create(url));
			
			StringEntity params =new StringEntity(doc,"UTF-8");
	        params.setContentType("application/json");
	        req.addHeader("content-type", "application/json");
	        req.addHeader("Accept-Encoding", "gzip,deflate,sdch");
	        req.setEntity(params);
			
	        log.info("Submitting document: " + url);
			response = httpclient.execute(target, req, localContext);
			responseCode = response.getStatusLine().getStatusCode();
			responseReason = response.getStatusLine().getReasonPhrase(); 
			
			// verify that the response was a 200, 201 or 202.
			// If it wasn't, the submission has failed.
			log.info("	Info: Response code: " + responseCode + " : " + responseReason);
			if (responseCode != HttpStatus.SC_OK && responseCode != HttpStatus.SC_CREATED && responseCode != HttpStatus.SC_ACCEPTED) {      
				log.info("	Error: upload to document server failed: " + responseReason);
				success = false;		
			} 

		} catch (UnsupportedEncodingException e) {
			success = false;
			String msg = "UnsupportedCodingException:" + e.getMessage();
			log.info("        " + msg);
		} catch(ClientProtocolException e) {
			success = false;
			String msg = "ClientProtocolException:" + e.getMessage();
			log.info("        " + msg);
		} catch(IOException e) {
			success = false;
			String msg = "IOException:" + e.getMessage();
			log.info("        " + msg);
		} catch(IllegalArgumentException e) {
			success = false;		
			String msg = "IllegalArgumentException:" + e.getMessage();
			log.info("        " + msg);
		} finally {
			try {
				httpclient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return success;
	}
	
	/*
	 * Apply Reminder notifications
	 * Triggered by a time period
	 */
	private void applyReminderNotifications(Connection sd, Connection cResults, String basePath, String serverName) {

		// Sql to get notifications that need a reminder
		String sql = "select "
				+ "t.id as t_id, "
				+ "n.id as f_id, "
				+ "a.id as a_id, "
				+ "t.survey_ident, "
				+ "t.update_id,"
				+ "t.p_id,"
				+ "n.target,"
				+ "n.remote_user,"
				+ "n.notify_details "
				+ "from tasks t, assignments a, forward n "
				+ "where t.tg_id = n.tg_id "
				+ "and t.id = a.task_id "
				+ "and n.enabled "
				+ "and n.trigger = 'task_reminder' "
				+ "and a.status = 'accepted' "
				//+ "and a.assigned_date < now() - cast(n.period as interval) "	// use schedule at however could allow assigned date to be used
				+ "and t.schedule_at < now() - cast(n.period as interval) "
				+ "and a.id not in (select a_id from reminder where n_id = n.id)";
		PreparedStatement pstmt = null;
		
		// Sql to record a reminder being sent
		String sqlSent = "insert into reminder (n_id, a_id, reminder_date) values (?, ?, now())";
		PreparedStatement pstmtSent = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);
			pstmtSent = sd.prepareStatement(sqlSent);
			
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			HashMap<Integer, ResourceBundle> locMap = new HashMap<> ();
			MessagingManager mm = new MessagingManager();
			
			ResultSet rs = pstmt.executeQuery();
			int idx = 0;
			while (rs.next()) {
				
				if(idx++ == 0) {
					System.out.println("\n-------------");
				}
				int tId = rs.getInt(1);
				int nId = rs.getInt(2);
				int aId = rs.getInt(3);
				String surveyIdent = rs.getString(4);
				String instanceId = rs.getString(5);
				int pId = rs.getInt(6);
				String target = rs.getString(7);
				String remoteUser = rs.getString(8);
				String notifyDetailsString = rs.getString(9);
				NotifyDetails nd = new Gson().fromJson(notifyDetailsString, NotifyDetails.class);
				
				int oId = GeneralUtilityMethods.getOrganisationIdForNotification(sd, nId);
				
				// Send the reminder
				SubmissionMessage subMgr = new SubmissionMessage(
						tId,
						surveyIdent,
						pId,
						instanceId, 
						nd.from,
						nd.subject, 
						nd.content,
						nd.attach,
						nd.include_references,
						nd.launched_only,
						nd.emailQuestion,
						nd.emailQuestionName,
						nd.emailMeta,
						nd.emails,
						target,
						remoteUser,
						"https",
						serverName,
						basePath);
				mm.createMessage(sd, oId, "reminder", "", gson.toJson(subMgr));
				
				// record the sending of the notification
				pstmtSent.setInt(1, nId);
				pstmtSent.setInt(2, aId);
				pstmtSent.executeUpdate();
				
				// Write to the log
				ResourceBundle localisation = locMap.get(nId);
				if(localisation == null) {
					Organisation organisation = GeneralUtilityMethods.getOrganisation(sd, oId);
					Locale orgLocale = new Locale(organisation.locale);
					localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", orgLocale);
				}
				
				String logMessage = "Reminder sent for: " + nId;
				if(localisation != null) {
					logMessage = localisation.getString("lm_reminder");
					logMessage = logMessage.replaceAll("%s1", GeneralUtilityMethods.getNotificationName(sd, nId));
				}
				lm.writeLogOrganisation(sd, oId, "subscriber", LogManager.REMINDER, logMessage);
				
				
			}
			

		} catch (Exception e) {
			e.printStackTrace();
		} finally {

			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtSent != null) {pstmtSent.close();}} catch (SQLException e) {}
			
		}
	}

}
