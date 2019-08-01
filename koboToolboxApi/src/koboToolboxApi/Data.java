package koboToolboxApi;
/*
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

 */

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import managers.DataManager;
import model.DataEndPoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.CustomReportsManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.RecordEventManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.SurveySettingsManager;
import org.smap.sdal.managers.SurveyViewManager;
import org.smap.sdal.managers.TableDataManager;
import org.smap.sdal.model.ConsoleTotals;
import org.smap.sdal.model.DataItemChangeEvent;
import org.smap.sdal.model.Instance;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.model.SqlParam;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.SurveySettingsDefn;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.TableColumn;

/*
 * Provides access to collected data
 */
@Path("/v1/data")
public class Data extends Application {

	Authorise a = null;
	Authorise aSuper = null;

	private static Logger log =
			Logger.getLogger(Data.class.getName());

	LogManager lm = new LogManager();		// Application log

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Data.class);
		return s;
	}

	public Data() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.MANAGE);
		a = new Authorise(authorisations, null);

		ArrayList<String> authorisationsSuper = new ArrayList<String> ();	
		authorisationsSuper.add(Authorise.ANALYST);
		authorisationsSuper.add(Authorise.VIEW_DATA);
		authorisationsSuper.add(Authorise.ADMIN);
		aSuper = new Authorise(authorisationsSuper, null);

	}

	/*
	 * KoboToolBox API version 1 /data
	 * Returns a list of data end points
	 */
	@GET
	@Produces("application/json")
	public Response getData(@Context HttpServletRequest request) { 

		Response response = null;
		String connectionString = "koboToolBoxAPI-getData";

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		aSuper.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		try {
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			DataManager dm = new DataManager(localisation);
			
			ArrayList<DataEndPoint> data = dm.getDataEndPoints(sd, request, false);

			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			String resp = gson.toJson(data);
			response = Response.ok(resp).build();
		} catch (SQLException e) {
			e.printStackTrace();
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection(connectionString, sd);
		}

		return response;
	}

	/*
	 * KoboToolBox API version 1 /data
	 * Get records for an individual survey in JSON format
	 * Survey and form identifiers are strings
	 */
	@GET
	@Produces("application/json")
	@Path("/{sIdent}")
	public void getDataRecordsService(@Context HttpServletRequest request,
			@Context HttpServletResponse response,
			@PathParam("sIdent") String sIdent,
			@QueryParam("start") int start,				// Primary key to start from
			@QueryParam("limit") int limit,				// Number of records to return
			@QueryParam("mgmt") boolean mgmt,
			@QueryParam("groupSurvey") String groupSurvey,	// Console
			@PathParam("view") int viewId,					// Console
			@QueryParam("schema") boolean schema,			// Console return schema with the data
			@QueryParam("group") boolean group,			// If set include a dummy group value in the response, used by duplicate query
			@QueryParam("sort") String sort,				// Column Human Name to sort on
			@QueryParam("dirn") String dirn,				// Sort direction, asc || desc
			@QueryParam("form") String formName,			// Form name (optional only specify for a child form)
			@QueryParam("start_parkey") int start_parkey,// Parent key to start from
			@QueryParam("parkey") int parkey,			// Parent key (optional, use to get records that correspond to a single parent record)
			@QueryParam("hrk") String hrk,				// Unique key (optional, use to restrict records to a specific hrk)
			@QueryParam("format") String format,			// dt for datatables otherwise assume kobo
			@QueryParam("bad") String include_bad,		// yes | only | none Include records marked as bad
			@QueryParam("audit") String audit_set,		// if yes return audit data
			@QueryParam("merge_select_multiple") String merge, 	// If set to yes then do not put choices from select multiple questions in separate objects
			@QueryParam("tz") String tz,					// Timezone
			@QueryParam("geojson") String geojson,		// if set to yes then format as geoJson
			@QueryParam("links") String links,
			@QueryParam("filter") String filter,
			@QueryParam("dateName") String dateName,			// Name of question containing the date to filter by
			@QueryParam("startDate") Date startDate,
			@QueryParam("endDate") Date endDate,
			@QueryParam("getSettings") boolean getSettings			// if set true get the settings from the database
			) throws ApplicationException, Exception { 
			
		boolean incLinks = false;
		if(links != null && (links.equals("true") || links.equals("yes"))) {
			incLinks = true;
		}
		if(formName != null) {
			incLinks = false;		// Links can only be specified for the main form
		}
		
		// Authorisation is done in getDataRecords
		getDataRecords(request, response, sIdent, start, limit, mgmt, groupSurvey, viewId, 
				schema, group, sort, dirn, formName, start_parkey,
				parkey, hrk, format, include_bad, audit_set, merge, geojson, tz, incLinks, 
				filter, dateName, startDate, endDate, getSettings);
	}
	
	/*
	 * KoboToolBox API version 1 /data
	 * Get a single data record in json format
	 */
	@GET
	@Produces("application/json")
	@Path("/{sIdent}/{uuid}")
	public Response getSingleDataRecord(@Context HttpServletRequest request,
			@Context HttpServletResponse response,
			@PathParam("sIdent") String sIdent,
			@PathParam("uuid") String uuid,		
			@QueryParam("merge_select_multiple") String merge, 	// If set to yes then do not put choices from select multiple questions in separate objects
			@QueryParam("tz") String tz,					// Timezone
			@QueryParam("geojson") String geojson		// if set to yes then format as geoJson
			) throws ApplicationException, Exception { 
		
		// Authorisation is done in getSingleRecord
		return getSingleRecord(request,
				sIdent,
				uuid,
				merge, 			// If set to yes then do not put choices from select multiple questions in separate objects
				tz				// Timezone
				);
		
	}
	
	/*
	 * Get changes to a record
	 */
	@GET
	@Produces("application/json")
	@Path("/changes/{sId}/{key}")
	public Response getRecordChanges(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("key") String key,		
			@QueryParam("tz") String tz,					// Timezone
			@QueryParam("geojson") String geojson		// if set to yes then format as geoJson
			) throws ApplicationException, Exception { 
		
		Response response = null;
		String connectionString = "koboToolboxApi - get data changes";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
			
		Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		Connection cResults = ResultsDataSource.getConnection(connectionString);
		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			String tableName = GeneralUtilityMethods.getMainResultsTable(sd, cResults, sId);
			String thread = GeneralUtilityMethods.getThread(cResults, tableName, key);
			RecordEventManager rem = new RecordEventManager(localisation, tz);
			ArrayList<DataItemChangeEvent> changeEvents = rem.getChangeEvents(sd, tableName, thread);
			
			response = Response.ok(gson.toJson(changeEvents)).build();
		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE, "Exception", e);
		} finally {
			SDDataSource.closeConnection(connectionString, sd);
			ResultsDataSource.closeConnection(connectionString, cResults);	
		}
		
		return response;
		
	}
	
	/*
	 * KoboToolBox API version 1 /data
	 * Get records for an individual survey in JSON format
	 */
	private void getDataRecords(HttpServletRequest request,
			HttpServletResponse response,
			String sIdent,
			int start,				// Primary key to start from
			int limit,				// Number of records to return
			boolean mgmt,
			String groupSurvey,		// Console
			int viewId,				// Console
			boolean schema,			// Console - return schema
			boolean group,			// If set include a dummy group value in the response, used by duplicate query
			String sort,				// Column Human Name to sort on
			String dirn,				// Sort direction, asc || desc
			String formName,			
			int start_parkey,		// Parent key to start from
			int parkey,				// Parent key (optional, use to get records that correspond to a single parent record)
			String hrk,				// Unique key (optional, use to restrict records to a specific hrk)
			String format,			// dt for datatables otherwise assume kobo
			String include_bad,		// yes | only | none Include records marked as bad
			String audit_set,		// if yes return audit data
			String merge, 			// If set to yes then do not put choices from select multiple questions in separate objects
			String geojson,			// If set to yes then render as geoJson rather than the kobo toolbox structure
			String tz,				// Timezone
			boolean incLinks	,
			String advanced_filter,
			String dateName,
			Date startDate,
			Date endDate,
			boolean getSettings		// Set true if the settings are stored in the database, otherwise they are passed with the request
			) throws ApplicationException, Exception { 

		String connectionString = "koboToolboxApi - get data records";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		int sId = 0;
		int fId = 0;
		try {
			/*
			 * Hack - some older clients still pass the survey id rather than the ident
			 * Until these are fixed handle either
			 */
			if(sIdent.startsWith("s")) {
				sId = GeneralUtilityMethods.getSurveyId(sd, sIdent);		// Ident - the correct way
			} else {
				sId = Integer.parseInt(sIdent);							// Id the old way
			}
			if(formName != null) {
				fId = GeneralUtilityMethods.getFormId(sd, sId, formName);
			}
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		if(viewId > 0) {
			a.isValidView(sd, request.getRemoteUser(), viewId, false);
		}
		if(groupSurvey != null) {
			a.isValidGroupSurvey(sd, request.getRemoteUser(), sId, groupSurvey);
		}		
		// End Authorisation

		String language = "none";

		Connection cResults = ResultsDataSource.getConnection(connectionString);

		//String sqlGetManagedId = "select managed_id from survey where s_id = ?";
		//PreparedStatement pstmtGetManagedId = null;

		String sqlGetMainForm = "select f_id, table_name from form where s_id = ? and parentform = 0;";
		PreparedStatement pstmtGetMainForm = null;

		String sqlGetForm = "select parentform, table_name from form where s_id = ? and f_id = ?;";
		PreparedStatement pstmtGetForm = null;

		PreparedStatement pstmt = null;

		String table_name = null;
		int parentform = 0;
		int managedId = 0;
		boolean getParkey = false;
		ResultSet rs = null;

		if(sort != null && dirn == null) {
			dirn = "asc";
		}
		
		boolean audit=false;
		if(audit_set != null && (audit_set.equals("yes") || audit_set.equals("true"))) {
			audit = true;
		}
		
		boolean isGeoJson=false;
		if(geojson != null && (geojson.equals("yes") || geojson.equals("true"))) {
			isGeoJson = true;
		}
		
		boolean mergeSelectMultiple = false;
		if(merge != null && (merge.equals("yes") || merge.equals("true"))) {
			mergeSelectMultiple = true;
		}

		if(include_bad == null) {
			include_bad = "none";
		}

		boolean isDt = false;
		if(format != null && format.equals("dt")) {
			isDt = true;
		}
		
		if(tz == null) {
			tz = GeneralUtilityMethods.getOrganisationTZ(sd, 
					GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser()));
		}
		tz = (tz == null) ? "UTC" : tz;

		PrintWriter outWriter = null;
		try {

			lm.writeLog(sd, sId, request.getRemoteUser(), LogManager.VIEW, "Managed Forms or the API. " + (hrk == null ? "" : "Hrk: " + hrk));
			
			response.setContentType("application/json; charset=UTF-8");
			response.setCharacterEncoding("UTF-8");
			outWriter = response.getWriter();
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			if(!GeneralUtilityMethods.isApiEnabled(sd, request.getRemoteUser())) {
				throw new ApplicationException(localisation.getString("susp_api"));
			}
			
			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);

			/*
			 * Get the survey view
			 */
			SurveyViewManager svm = new SurveyViewManager(localisation, tz);
			SurveySettingsManager ssm = new SurveySettingsManager(localisation, tz);
			int uId = GeneralUtilityMethods.getUserId(sd, request.getRemoteUser());
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			
			SurveySettingsDefn ssd = null;
			SurveyViewDefn sv = null;
			ArrayList<TableColumn> columns = null;
			
			/*
			 * If the data is destined for the console then get the columns using the survey view class
			 * For normal API calls this is not used primarily to reduce the chance that anything gets changed due
			 * to console specific coding
			 */
			if(schema) {
				ssd = ssm.getSurveySettings(sd, uId, sIdent);
				if(!getSettings) {
					// Update the settings with the values passed in the request
					ssd.limit = limit;
					ssd.filter = advanced_filter;
					ssd.dateName = dateName;
					ssd.fromDate = startDate;
					ssd.toDate = endDate;
					ssd.overridenDefaultLimit = "yes";
					
					ssm.setSurveySettings(sd, uId, sIdent, ssd);
				} else {
					// If the limit has not previously been set then default it to 1,000
					if(ssd.overridenDefaultLimit == null) {
						ssd.limit = 1000;
					}
				}
				sv = svm.getSurveyView(sd, 
						cResults, 
						uId, 
						ssd, sId, managedId, request.getRemoteUser(), oId, superUser,
						groupSurvey);	
				columns = sv.columns;
				table_name = sv.tableName;			
				
			} else {
			
				// Get the managed Id
				//if(mgmt) {
				//	pstmtGetManagedId = sd.prepareStatement(sqlGetManagedId);
				//	pstmtGetManagedId.setInt(1, sId);
				//	rs = pstmtGetManagedId.executeQuery();
				//	if(rs.next()) {
				//		managedId = rs.getInt(1);
				//	}
				//	if(rs != null) try {rs.close(); rs = null;} catch(Exception e) {}
				//}
	
				if(fId == 0) {
					pstmtGetMainForm = sd.prepareStatement(sqlGetMainForm);
					pstmtGetMainForm.setInt(1,sId);
	
					log.info("Getting main form: " + pstmtGetMainForm.toString() );
					rs = pstmtGetMainForm.executeQuery();
					if(rs.next()) {
						fId = rs.getInt(1);
						table_name = rs.getString(2);
					}
					if(rs != null) try {rs.close(); rs = null;} catch(Exception e) {}
				} else {
					getParkey = true;
					pstmtGetForm = sd.prepareStatement(sqlGetForm);
					pstmtGetForm.setInt(1,sId);
					pstmtGetForm.setInt(2,fId);
	
					log.info("Getting specific form: " + pstmtGetForm.toString() );
					rs = pstmtGetForm.executeQuery();
					if(rs.next()) {
						parentform = rs.getInt(1);
						table_name = rs.getString(2);
					}
					if(rs != null) try {rs.close(); rs = null;} catch(Exception e) {}
				}
	
				columns = GeneralUtilityMethods.getColumnsInForm(
						sd,
						cResults,
						localisation,
						language,
						sId,
						sIdent,
						request.getRemoteUser(),
						null,
						parentform,
						fId,
						table_name,
						true,		// Read Only
						getParkey,	// Include parent key if the form is not the top level form (fId is 0)
						(include_bad.equals("yes") || include_bad.equals("only")),
						true,		// include instance id
						true,		// Include prikey
						true,		// include other meta data
						true,		// include preloads
						true,		// include instancename
						true,		// include survey duration
						superUser,
						false,		// TODO include HXL
						audit,
						tz,
						mgmt			// If this is a management request then include the assigned user after prikey
						);
	
				//if(mgmt && managedId > 0) {
				//	CustomReportsManager crm = new CustomReportsManager ();
				//	ReportConfig config = crm.get(sd, managedId, -1);
				//	if(config != null) {
				//		columns.addAll(config.columns);
				//	}
				//}
			}
			
			TableDataManager tdm = new TableDataManager(localisation, tz);
			pstmt = tdm.getPreparedStatement(
					sd, 
					cResults,
					columns,
					urlprefix,
					sId,
					table_name,
					parkey,
					hrk,
					request.getRemoteUser(),
					null,	// roles
					sort,
					dirn,
					mgmt,
					group,
					isDt,
					start,
					getParkey,
					start_parkey,
					superUser,
					false,			// Return records greater than or equal to primary key
					include_bad,
					null	,			// no custom filter
					null,			// key filter
					tz,
					null,			// instanceId
					ssd.filter,
					ssd.dateName,
					ssd.fromDate,
					ssd.toDate
					);
			
			ConsoleTotals totals = new ConsoleTotals();
			// Write array start
			if(isDt) {
				outWriter.print("{\"data\":");
			}
			if(isGeoJson) {
				outWriter.print("{\"type\":\"FeatureCollection\",");		// type
																		// TODO metadata
				outWriter.print("\"features\":");						// Features
			}
			
			// Add feature data
			outWriter.print("[");
			
			if(pstmt != null) {
				log.info("KoboAPI data: " + pstmt.toString());
				/*
				 * Get the data record by record so it can be streamed
				 */
				
				// page the results to reduce memory usage
				log.info("---------------------- paging results to postgres");
				cResults.setAutoCommit(false);		
				pstmt.setFetchSize(100);	
				
				rs = pstmt.executeQuery();
				JSONObject jo = new JSONObject();
				int index = 0;
				while(jo != null) {
					
					jo =  tdm.getNextRecord(
							sd,
							rs,
							columns,
							urlprefix,
							group,
							isDt,
							limit,
							mergeSelectMultiple,
							isGeoJson,
							incLinks	,
							sIdent
							);
					if(jo != null) {
						if(index > 0) {
							outWriter.print(",");
						}
						outWriter.print(jo.toString());
					}
					
					index++;
					if (ssd.limit > 0 && index >= ssd.limit) {
						totals.reached_limit = true;
						break;
					}

				}
				
				cResults.setAutoCommit(true);		// page the results to reduce memory
				
			}
			
			outWriter.print("]");
			if(isDt) {
				if(schema) {
					/*
					 * Return the schema with the data 
					 * 1. remove data not needed by the client for performance and security reasons
					 */
					for(TableColumn tc : sv.columns) {
						tc.actions = null;
						tc.calculation = null;
					}
					sv.tableName = null;
					
					// 2. Add the schema to the results
					outWriter.print(",\"schema\":");
					outWriter.print(gson.toJson(sv));		// Add the survey view
					
					// 3. Add the survey settings to the results
					if(getSettings) {
						outWriter.print(",\"settings\":");
						outWriter.print(gson.toJson(ssd));
					}
					
					// 4. Add totals to the results
					outWriter.print(",\"totals\":");
					outWriter.print(gson.toJson(totals));
				}
				
				outWriter.print("}");
			}
			
			if(isGeoJson) {				// TODO bbox										
				outWriter.print("}");	// close
			}

		} catch (Exception e) {
			try {cResults.setAutoCommit(true);} catch(Exception ex) {};
			log.log(Level.SEVERE, "Exception", e);
			outWriter.print(e.getMessage());
		} finally {

			outWriter.flush(); 
			outWriter.close();
			
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetMainForm != null) {pstmtGetMainForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetForm != null) {pstmtGetForm.close();	}} catch (SQLException e) {	}
			//try {if (pstmtGetManagedId != null) {pstmtGetManagedId.close();	}} catch (SQLException e) {	}

			ResultsDataSource.closeConnection(connectionString, cResults);			
			SDDataSource.closeConnection(connectionString, sd);
		}

		//return response;

	}
	
	/*
	 * KoboToolBox API version 1 /data
	 * Get records for an individual survey in JSON format
	 */
	private Response getSingleRecord(HttpServletRequest request,
			String sIdent,
			String uuid,
			String merge, 			// If set to yes then do not put choices from select multiple questions in separate objects
			String tz				// Timezone
			) throws ApplicationException, Exception { 

		Response response;
		
		String connectionString = "koboToolboxApi - get data records";
		
		Connection cResults = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		
		int sId = GeneralUtilityMethods.getSurveyId(sd, sIdent);	
		
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation

		tz = (tz == null) ? "UTC" : tz;

		try {

			cResults = ResultsDataSource.getConnection(connectionString);
			
			lm.writeLog(sd, sId, request.getRemoteUser(), LogManager.VIEW, "Managed Forms or the API. ");
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			if(!GeneralUtilityMethods.isApiEnabled(sd, request.getRemoteUser())) {
				throw new ApplicationException(localisation.getString("susp_api"));
			}

			SurveyManager sm = new SurveyManager(localisation, tz);
			
			Survey s = sm.getById(
					sd, 
					cResults, 
					request.getRemoteUser(),
					sId, 
					true, 		// full
					null, 		// basepath
					null, 		// instance id
					false, 		// get results
					false, 		// generate dummy values
					true, 		// get property questions
					false, 		// get soft deleted
					true, 		// get HRK
					"external", 		// get external options
					false, 		// get change history
					false, 		// get roles
					true,		// superuser 
					null, 		// geomformat
					false, 		// reference surveys
					false		// only get launched
					);
			
			ArrayList<Instance> instances = sm.getInstances(
					sd,
					cResults,
					s,
					s.getFirstForm(),
					0,
					null,
					uuid,
					sm);
		
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
			
			// Just return the first instance - at the top level there should only ever be one
			response = Response.ok(gson.toJson(instances.get(0))).build();

		} catch (Exception e) {
			try {cResults.setAutoCommit(true);} catch(Exception ex) {};
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			ResultsDataSource.closeConnection(connectionString, cResults);			
			SDDataSource.closeConnection(connectionString, sd);
		}

		return response;

	}

	/*
	 * Get similar records for an individual survey in JSON format
	 */
	@GET
	@Produces("application/json")
	@Path("/similar/{sId}/{select}")
	public Response getSimilarDataRecords(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@PathParam("select") String select,			// comma separated list of qname::function
			//  where function is none || lower
			@QueryParam("start") int start,
			@QueryParam("limit") int limit,
			@QueryParam("mgmt") boolean mgmt,
			@QueryParam("form") int fId,				// Form id (optional only specify for a child form)
			@QueryParam("format") String format			// dt for datatables otherwise assume kobo
			) { 

		Response response = null;

		String connectionString = "koboToolboxApi - get similar data records";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		aSuper.isAuthorised(sd, request.getRemoteUser());
		aSuper.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation

		String language = "none";
		Connection cResults = ResultsDataSource.getConnection(connectionString);

		String sqlGetMainForm = "select f_id, table_name from form where s_id = ? and parentform = 0;";
		PreparedStatement pstmtGetMainForm = null;

		String sqlGetForm = "select parentform, table_name from form where s_id = ? and f_id = ?;";
		PreparedStatement pstmtGetForm = null;

		String sqlGetManagedId = "select managed_id from survey where s_id = ?";
		PreparedStatement pstmtGetManagedId = null;

		PreparedStatement pstmtGetSimilar = null;
		PreparedStatement pstmtGetData = null;

		String tz = "UTC";

		StringBuffer columnSelect = new StringBuffer();
		StringBuffer similarWhere = new StringBuffer();
		ArrayList<String> groupTypes = new ArrayList<String> ();
		int groupColumns = 0;
		String table_name = null;
		int parentform = 0;
		int managedId = 0;
		ResultSet rs = null;
		JSONArray ja = new JSONArray();

		boolean isDt = false;
		if(format != null && format.equals("dt")) {
			isDt = true;
		}

		try {
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			if(!GeneralUtilityMethods.isApiEnabled(sd, request.getRemoteUser())) {
				throw new ApplicationException(localisation.getString("susp_api"));
			}
			
			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);

			// Get the managed Id
			if(mgmt) {
				pstmtGetManagedId = sd.prepareStatement(sqlGetManagedId);
				pstmtGetManagedId.setInt(1, sId);
				rs = pstmtGetManagedId.executeQuery();
				if(rs.next()) {
					managedId = rs.getInt(1);
				}
				rs.close();
			}

			if(fId == 0) {
				pstmtGetMainForm = sd.prepareStatement(sqlGetMainForm);
				pstmtGetMainForm.setInt(1,sId);

				log.info("Getting main form: " + pstmtGetMainForm.toString() );
				rs = pstmtGetMainForm.executeQuery();
				if(rs.next()) {
					fId = rs.getInt(1);
					table_name = rs.getString(2);
				}
				rs.close();
			} else {
				pstmtGetForm = sd.prepareStatement(sqlGetForm);
				pstmtGetForm.setInt(1,sId);
				pstmtGetForm.setInt(2,fId);

				log.info("Getting specific form: " + pstmtGetForm.toString() );
				rs = pstmtGetForm.executeQuery();
				if(rs.next()) {
					parentform = rs.getInt(1);
					table_name = rs.getString(2);
				}
				rs.close();
			}

			String surveyIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
			ArrayList<TableColumn> columns = GeneralUtilityMethods.getColumnsInForm(
					sd,
					cResults,
					localisation,
					language,
					sId,
					surveyIdent,
					request.getRemoteUser(),
					null,
					parentform,
					fId,
					table_name,
					true,		// Read Only
					false,		// Don't include parent key
					false,		// Don't include "bad" columns
					true,		// include instance id
					true,		// Include prikey
					true,		// Include other meta data
					true,		// Include preloads
					true,		// Include instance name
					false,		// Include survey duration
					superUser,
					false,		// Only include HXL with CSV and Excel output
					false,
					tz,
					false		// mgmt
					);

			if(mgmt) {
				CustomReportsManager crm = new CustomReportsManager ();
				ReportConfig config = crm.get(sd, managedId, -1);
				columns.addAll(config.columns);
			}

			ArrayList<SqlParam> params = new ArrayList<> ();
			
			if(GeneralUtilityMethods.tableExists(cResults, table_name)) {

				/*
				 * 1. Prepare the data query minus the where clause that is created to select similar rows
				 */
				for(int i = 0; i < columns.size(); i++) {
					TableColumn c = columns.get(i);
					if(i > 0) {
						columnSelect.append(",");
					}
					columnSelect.append(c.getSqlSelect(urlprefix, tz, params));
				}


				String sqlGetData = "select " + columnSelect.toString() + " from " + table_name
						+ " where prikey >= ? "
						+ "and _bad = 'false' ";
				String sqlSelect = "";

				String sqlGetDataOrder = null;

				// Set default sort order
				if(mgmt) {
					sqlGetDataOrder = " order by prikey desc limit 10000";
				} else {
					sqlGetDataOrder = " order by prikey asc;";
				}

				/*
				 * 1. Find the groups of similar records
				 */
				columnSelect = new StringBuffer();
				String [] selectPairs = select.split(",");
				for(int i = 0; i < selectPairs.length; i++) {
					String [] aSelect = selectPairs[i].split("::");
					if(aSelect.length > 1) {
						for(int j = 0; j < columns.size(); j++) {
							if(columns.get(j).column_name.equals(aSelect[0])) {
								TableColumn c = columns.get(j);
								boolean stringFnApplies = false;

								if(c.type.equals("string") || c.type.equals("select1")
										|| c.type.equals("barcode")) {
									stringFnApplies = true;
								}

								if( groupColumns > 0) {
									columnSelect.append(",");
								}
								similarWhere.append(" and ");

								if(stringFnApplies 
										&& (aSelect[1].equals("lower") 
												|| aSelect[1].equals("soundex"))) {
									String s = aSelect[1] +"(" + c.getSqlSelect(urlprefix, tz, params) + ")";
									columnSelect.append(s);
									similarWhere.append(s + " = ?");
								} else {
									String s = c.getSqlSelect(urlprefix, tz, params);
									columnSelect.append(s);
									similarWhere.append(s + " = ?");
								}
								groupColumns++;
								groupTypes.add(c.type);
								break;
							}
						}
					}
				}

				if(columnSelect.length() == 0) {
					throw new Exception("No Matching Columns");
				}

				String sqlGetSimilar = "select count(*), " + columnSelect.toString()
				+ " from " + table_name
				+ " where prikey >= ? "
				+ "and _bad = 'false'";
				String sqlGroup = " group by " + columnSelect.toString();
				String sqlHaving = " having count(*) > 1 ";


				pstmtGetSimilar = cResults.prepareStatement(sqlGetSimilar + sqlGroup + sqlHaving);
				
				// Set parameters
				int paramCount = 1;			
				pstmtGetSimilar.setInt(paramCount++, start);
				
				rs = pstmtGetSimilar.executeQuery();

				/*
				 * For each grouping of similar records get the individual records
				 */
				while(rs.next()) {


					/*
					 * 3. Get the data that make up these similar records
					 */
					String groupKey = "";
					pstmtGetData = cResults.prepareStatement(sqlGetData + sqlSelect + similarWhere.toString() + sqlGetDataOrder);
					
					paramCount = 1;
					paramCount = GeneralUtilityMethods.addSqlParams(pstmtGetSimilar, paramCount, params);	// Parameters in select clause
					
					pstmtGetData.setInt(paramCount++, start);
					for(int i = 0; i < groupColumns; i++) {
						String gType = groupTypes.get(i);
						log.info("Adding group type: " + gType);
						if(gType.equals("int")) {
							pstmtGetData.setInt(paramCount++, rs.getInt(i + 2));	
						} else { 
							pstmtGetData.setString(paramCount++, rs.getString(i + 2));
						}
						if(i > 0) {
							groupKey += "::";
						}
						groupKey += rs.getString(i + 2);
					}
					log.info("Get data: " + pstmtGetData.toString());
					ResultSet rsD = pstmtGetData.executeQuery();

					int index = 0;
					while (rsD.next()) {

						if(limit > 0 && index >= limit) {
							break;
						}
						index++;

						JSONObject jr = new JSONObject();
						jr.put("_group", groupKey);
						for(int i = 0; i < columns.size(); i++) {	

							TableColumn c = columns.get(i);
							String name = null;
							String value = null;

							if(c.isGeometry()) {							
								// Add Geometry (assume one geometry type per table)
								String geomValue = rsD.getString(i + 1);	
								
								name = "_geolocation";
								JSONArray coords = null;
								if(geomValue != null) {
									JSONObject jg = new JSONObject(geomValue);									
									coords = jg.getJSONArray("coordinates");
								} else {
									coords = new JSONArray();
								}
								jr.put(name, coords);

							} else {

								name = c.column_name;
								value = rsD.getString(i + 1);	

								if(value == null) {
									value = "";
								} else if(c.type.equals("dateTime")) {
									value = value.replaceAll("\\.[0-9]{3}", "");
								}

								if(name != null ) {
									if(!isDt) {
										name = GeneralUtilityMethods.translateToKobo(name);
									}
									jr.put(name, value);
								}
							}


						}

						ja.put(jr);
					}
					rsD.close();
				}

				rs.close();
			}


			if(isDt) {
				JSONObject dt  = new JSONObject();
				dt.put("data", ja);
				response = Response.ok(dt.toString()).build();
			} else {
				response = Response.ok(ja.toString()).build();
			}




		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {

			try {if (pstmtGetMainForm != null) {pstmtGetMainForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetForm != null) {pstmtGetForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetData != null) {pstmtGetData.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetSimilar != null) {pstmtGetSimilar.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetManagedId != null) {pstmtGetManagedId.close();	}} catch (SQLException e) {	}

			ResultsDataSource.closeConnection(connectionString, cResults);			
			SDDataSource.closeConnection(connectionString, sd);
		}

		return response;

	}


}

