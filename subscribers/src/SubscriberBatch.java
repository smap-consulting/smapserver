import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.smap.model.SurveyInstance;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.managers.MessagingManagerApply;
import org.smap.sdal.managers.NotificationManager;
import org.smap.sdal.managers.ServerManager;
import org.smap.sdal.model.Notification;
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
	PreparedStatement pstmt = null;
	
	
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
		
		String dbClass = null;
		String database = null;
		String user = null;
		String password = null;
		JdbcUploadEventManager uem = null;
		
		try {
			db = dbf.newDocumentBuilder();
			xmlConf = db.parse(new File(confFilePath + "/metaDataModel.xml"));
			dbClassMeta = xmlConf.getElementsByTagName("dbclass").item(0).getTextContent();
			databaseMeta = xmlConf.getElementsByTagName("database").item(0).getTextContent();
			userMeta = xmlConf.getElementsByTagName("user").item(0).getTextContent();
			passwordMeta = xmlConf.getElementsByTagName("password").item(0).getTextContent();
			
			// Get the connection details for the target results database
			xmlConf = db.parse(new File(confFilePath + "/results_db.xml"));
			dbClass = xmlConf.getElementsByTagName("dbclass").item(0).getTextContent();
			database = xmlConf.getElementsByTagName("database").item(0).getTextContent();
			user = xmlConf.getElementsByTagName("user").item(0).getTextContent();
			password = xmlConf.getElementsByTagName("password").item(0).getTextContent();
			
			Class.forName(dbClassMeta);
			sd = DriverManager.getConnection(databaseMeta, userMeta, passwordMeta);	
			cResults = DriverManager.getConnection(database, user, password);
		
			uem = new JdbcUploadEventManager(sd);
			
			/*
			 * Get subscribers and their configuration
			 * This is re-evaluated every time the batch job is run to allow
			 * configurations to be updated and applied immediately
			 * However it may be better to add an external trigger that forces the
			 * configuration files to be re-read when directed by the administrator
			 */
			List<Subscriber> subscribers = null;
			if(subscriberType.equals("upload")) {
				subscribers = init(sd, pstmt);		// Get subscribers 
			} else if(subscriberType.equals("forward")) {
				subscribers = initForward(sd, pstmt);		// Get subscribers 
			} else {
				System.out.println("Unknown subscriber type: " + subscriberType + " known values are upload, forward");
			}
		
			Date timeNow = new Date();
			if(subscribers == null || subscribers.isEmpty()) {
				System.out.println("    No subscribers" + timeNow.toString());
			} else {
				
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
							System.out.println("\nUploading subscriber: " + s.getSubscriberName() + " : " + timeNow.toString());
		
							for(UploadEvent ue : uel) {
								System.out.println("        Survey:" + ue.getSurveyName() + ":" + ue.getId());
		
								SurveyInstance instance = null;
								SubscriberEvent se = new SubscriberEvent();
								se.setSubscriber(s.getSubscriberName());
								se.setDest(s.getDest());
								String uploadFile = ue.getFilePath();
								String auditFile = ue.getAuditFilePath();
								
								System.out.println("Upload file: " + uploadFile);
								InputStream is = null;
								InputStream is2 = null;
								InputStream is3 = null;
								
								try {
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
										System.out.println("UUID:" + instance.getUuid());
										
										//instance.getTopElement().printIEModel("   ");	// Debug 
								
										// Get the template for this survey
										String templateName = instance.getTemplateName();
										SurveyTemplate template = new SurveyTemplate();
										
										template.readDatabase(sd, templateName, false);					
										template.extendInstance(sd, instance, true);	// Extend the instance with information from the template
										// instance.getTopElement().printIEModel("   ");	// Debug
										
										// Get attachments from incomplete submissions
										getAttachmentsFromIncompleteSurveys(sd, s.getSubscriberName(), ue.getFilePath(), ue.getOrigSurveyIdent(), ue.getIdent());
										
										is3 = new FileInputStream(uploadFile);	// Get an input stream for the file in case the subscriber uses that rather than an Instance object
										s.upload(instance, is3, ue.getUserName(), 
												ue.getServerName(), ue.getImei(), se,
												confFilePath, ue.getFormStatus(),
												basePath, uploadFile, ue.getUpdateId(),
												ue.getId(),
												ue.getUploadTime(),
												ue.getSurveyNotes(),
												ue.getLocationTrigger(),
												ue.getAuditFilePath());	// Call the subscriber	
									
									} else {
										
										System.out.println("        filtered");
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
										
										String sqlUpdateStatus = "insert into subscriber_event ("
												+ "se_id,"
												+ "ue_id,"
												+ "subscriber,"
												+ "status,"
												+ "reason,"
												+ "dest) "
												+ "values (nextval('se_seq'), ?, ?, ?, ?, ?);";
										PreparedStatement pstmt = null;
										try {
											pstmt = sd.prepareStatement(sqlUpdateStatus);
											pstmt.setInt(1, ue.getId());
											pstmt.setString(2, se.getSubscriber());
											pstmt.setString(3, se.getStatus());
											pstmt.setString(4, se.getReason());
											pstmt.setString(5, se.getDest());
											pstmt.executeUpdate();
										} finally {
											if(pstmt != null) try {pstmt.close();} catch(Exception e) {}
										}
		
									} else if(se.getStatus() != null && se.getStatus().equals("host_unreachable")) {
										// If the host is unreachable then stop forwarding for 10 seconds
										// Also stop processing this subscriber, it may be that it has been taken off line

										int forwardSleep = 60;
										Date now = new Date();
										String dt = DateFormat.getDateTimeInstance().format(now);
										System.out.println("No connectivity: " + dt);
										try {
											Thread.sleep(forwardSleep * 1000);
										} catch (Exception e) {
											// ignore
										}
									}
								}	
								
								// If the host is unreachable stop processing this subscriber, it may be that it has been taken off line
								if(se.getStatus() != null && se.getStatus().equals("host_unreachable")) {
									System.out.println("Stopping processing of subscriber: " + s.getSubscriberName());
									break;
								}
							}
						}
					}
				}
			}
			
			// Erase any templates that were deleted more than a set time ago
			eraseOldTemplates(sd, cResults, basePath);
			
			MessagingManagerApply mma = new MessagingManagerApply();
			mma.applyOutbound(sd, "smap");
			subscribers = null;
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
			
			if(uem != null) {uem.close();}
			
			try {				
				if (sd != null) {
					sd.close();
					sd = null;
				}
			} catch (SQLException e) {
				System.out.println("Failed to close connection");
				e.printStackTrace();
			}
			
			try {
				if (cResults != null) {
					cResults.close();
					cResults = null;
				}
			} catch (SQLException e) {
				System.out.println("Failed to close results connection");
				e.printStackTrace();
			}
		}

	}
	
	/*
	 * Erase deleted templates more than a specified number of days old
	 */
	private void eraseOldTemplates(Connection sd, Connection cResults, String basePath) {
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtTemp = null;
		
		try {
			
			ServerManager sm = new ServerManager();
			String sql = "select s_id, "
					+ "p_id, "
					+ "last_updated_time, "
					+ "ident,"
					+ "display_name "
					+ "from survey where deleted "
					+ "and ((last_updated_time < now() - interval '100 days') or last_updated_time is null) "
					+ "order by last_updated_time;";
			pstmt = sd.prepareStatement(sql);
			
			String sqlTemp = "update survey set last_updated_time = ? where s_id = ?";
			pstmtTemp = sd.prepareStatement(sqlTemp);
			
			/*
			 * Temporary fix for lack of accurate date when a survey was deleted
			 */
			ResultSet rs = pstmt.executeQuery();
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
					System.out.println("******** Failed to get date from: " + surveyDisplayName + " deleted date was: " + deletedDate);
				} else {
					try {
						java.sql.Date dx = java.sql.Date.valueOf(date);
						pstmtTemp.setDate(1, dx);
						pstmtTemp.setInt(2,  sId);
						pstmtTemp.executeUpdate();	
					} catch (Exception e) {
						System.out.println("Error: " + surveyDisplayName + " : " + e.getMessage());
					}
				}

			}
			
			/*
			 * Process surveys to be deleted for real now
			 */
			rs = pstmt.executeQuery();	
			while(rs.next()) {
				int sId = rs.getInt("s_id");
				int projectId = rs.getInt("p_id");
				String deletedDate = rs.getString("last_updated_time");
				String surveyIdent = rs.getString("ident");
				String surveyDisplayName = rs.getString("display_name");
				
				System.out.println("######### Erasing: " + surveyDisplayName + " which was deleted on " +  deletedDate);
				sm.deleteSurvey(sd, cResults, "auto erase", projectId, sId, surveyIdent, surveyDisplayName, basePath, true, "yes");
			}

				
		} catch (Exception e) {
			e.printStackTrace();
		} finally {			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}	
			try {if (pstmtTemp != null) {pstmtTemp.close();}} catch (SQLException e) {}	
		}
	}
	/*
	 * Create a Subscriber object for each subscriber
	 */
	public List<Subscriber> init(Connection connection, PreparedStatement pstmt) throws SQLException {
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
						System.out.println("SAXException on configuration file: " + confFile);
					} catch (IOException e) {
						System.out.println("IOException on configuration file: " + confFile);
					} catch(ClassNotFoundException e) {
						System.out.println("ClassNotFoundException on configuration file: " + confFile + " with class: " + subscriberType);
					} catch (IllegalAccessException e) {
						System.out.println("IllegalAccessException on configuration file: " + confFile + " with class: " + subscriberType);
					} catch (InstantiationException e) {
						System.out.println("InstantiationException on configuration file: " + confFile + " with class: " + subscriberType);
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
	public List<Subscriber> initForward(Connection connection, PreparedStatement pstmt) throws SQLException {
		
		List<Subscriber> subscribers = new ArrayList<Subscriber> ();
		
		// This type of subscriber is per link, that is 
		// survey -> remote survey so create a subscriber object for each
		// survey id and remote end point to be forwarded
		NotificationManager fm = new NotificationManager();
		ArrayList<Notification> forwards = fm.getEnabledNotifications(connection, pstmt, true);
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
				sub.setSubscriberName("fwd_" + sId + "_" + host + remote_sId);
				sub.setSurveyId(sId);	
				sub.setSurveyIdRemote(remote_sId);
				sub.setUser(f.remote_user);
				sub.setPassword(f.remote_password);
				sub.setSurveyNameRemote(f.remote_s_name);
				sub.setHostname(remoteUrl);
				subscribers.add(sub);	// Add the new subscriber to the list of subscribers
			} else {
				System.out.println("Error: Invalid host (" + remoteUrl + ") for survey " + sId);
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
				" and not exists (select se.se_id from subscriber_event se where se.ue_id = ue.ue_id)";
		
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
			
			System.out.println("SQL: " + sql + " : " + origIdent + " : " + ident);
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				System.out.println("++++++ Processing incomplete file name is: " + rs.getString(2));
				
				int ue_id = rs.getInt(1);
				File sourceFile = new File(rs.getString(2));
				File sourceDirFile = sourceFile.getParentFile();
				
				File files[] = sourceDirFile.listFiles();
				for(int i = 0; i < files.length; i++) {
					System.out.println("       File: " + files[i].getName());
					String fileName = files[i].getName();
					if(!fileName.endsWith("xml")) {
						System.out.println("++++++ Moving " + fileName + " to " + finalDir);
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

	
}
