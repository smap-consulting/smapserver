package surveyKPI;

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
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import model.Filter;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.RoleManager;
import org.smap.sdal.model.SqlFrag;
import org.smap.sdal.model.SqlFragParam;
import org.smap.sdal.model.TableColumn;

import utilities.QuestionInfo;
import utilities.Tables;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns data for the passed in table name
 */
@Path("/items/{form}")
public class Items extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Items.class.getName());

	LogManager lm = new LogManager();		// Application log
	
	/*
	 * JSON
	 * Usage /surveyKPI/items/{formId}?geom=yes|no&feats=yes|no&mustHaveGeom=yes|no
	 *   geom=yes  then location information will be returned
	 *   feats=yes then features associated with geometries will be returned
	 *   mustHaveGeom=yes then only items that have a location will be returned
	 *   The default is:
	 *   	to return all records unless rec_limit is set > 0
	 *   	Not return "bad" records unless get_bad is set to true
	 */
	@GET
	@Produces("application/json")
	public String getTable(@Context HttpServletRequest request,
			@PathParam("form") int fId, 
			@QueryParam("geom") String geom,			
			@QueryParam("mustHaveGeom") String mustHaveGeom,
			@QueryParam("start_key") int start_key,
			@QueryParam("get_bad") boolean bBad,		// Get bad records
			@QueryParam("rec_limit") int rec_limit,
			@QueryParam("dateId") int dateId,		// Id of question containing the date to filter by
			@QueryParam("startDate") Date startDate,
			@QueryParam("endDate") Date endDate,
			@QueryParam("filter") String sFilter) { 
		
		JSONObject jo = new JSONObject();
		boolean bGeom = true;
		boolean bMustHaveGeom = true;
		int maxRec = 0;
		int recCount = 0;

		ArrayList<String> colNames = new ArrayList<String> ();
		HashMap<String, String> surveyNames = new HashMap<String, String> ();
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";	

		if(geom != null && geom.equals("no")) {
			bGeom = false;
		}

		if(mustHaveGeom != null && mustHaveGeom.equals("no")) {
			bMustHaveGeom = false;
		}
			
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Items");
		int sId = 0;
		try {
			sId = GeneralUtilityMethods.getSurveyIdForm(sd, fId);
		} catch (Exception e) {
			
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		lm.writeLog(sd, sId, request.getRemoteUser(), "view", "View Results");
	
		Tables tables = new Tables(sId);
		boolean hasRbacRowFilter = false;
		
		if(fId > 0) {
			
			Connection connection = null;
			PreparedStatement pstmt = null;
			PreparedStatement pstmtSSC = null;
			PreparedStatement pstmtFDetails = null;
			
			int parent = 0;
			String tName = null;
			String formName = null;
			 
			try {
				
				// Get the users locale
				Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
				ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				
				// Connect to the results database
				connection = ResultsDataSource.getConnection("surveyKPI-Items");	
				
				// Prepare the statement to get the form details
				String sqlFDetails = "select f.table_name, f.parentform, name from question q, form f " +
					" where f.f_id = ?; ";
				pstmtFDetails = sd.prepareStatement(sqlFDetails);
				
				// Get the table details
				// Get the question type
				pstmtFDetails.setInt(1, fId);
				ResultSet rsDetails = pstmtFDetails.executeQuery();
				if(rsDetails.next()) {
					
					tName = rsDetails.getString(1);
					parent = rsDetails.getInt(2);
					formName = rsDetails.getString(3);
					
					tables.add(tName, fId, parent);
				}
				
				int geomIdx = -1;
				
				JSONObject jTotals = new JSONObject();
				jo.put("totals", jTotals);
				jTotals.put("start_key", start_key);
				
				// Get the number of records
				String sql = "SELECT count(*) FROM " + tName + ";";
				log.info("Get the number of records: " + sql);	
				pstmt = connection.prepareStatement(sql);	 			
				ResultSet resultSet = pstmt.executeQuery();
				if(resultSet.next()) {
					jTotals.put("total_count", resultSet.getInt(1));
				}
				
				// Get the number of bad records
				sql = "SELECT count(*) FROM " + tName + " where _bad = 'true';";
				log.info("Get the number of bad records: " + sql);
				if(pstmt != null) try {pstmt.close();} catch(Exception e) {};
				pstmt = connection.prepareStatement(sql);	 			
				resultSet = pstmt.executeQuery();
				if(resultSet.next()) {
					jTotals.put("bad_count", resultSet.getInt(1));
				}
	
				
				ArrayList<TableColumn> columnList = GeneralUtilityMethods.getColumnsInForm(
						sd,
						connection,
						sId,
						request.getRemoteUser(),
						parent,
						fId,
						tName,
						false,	// Don't include Read only
						true,	// Include parent key
						true,	// Include "bad"
						true,	// Include instanceId
						true	// Include other meta data
						);		
				
				// Construct a new query that retrieves a geometry object as geoJson
				StringBuffer cols = new StringBuffer("");
				String geomType = null;
				int newColIdx = 0;
				JSONArray columns = new JSONArray();
				ArrayList<String> sscList = new ArrayList<String> ();
				
				for(TableColumn c : columnList) {
					if(newColIdx > 0) {
						cols.append(",");
					}
					if(bGeom && c.type.equals("geopoint") || c.type.equals("geopolygon") 
							|| c.type.equals("geolinestring") || c.type.equals("geotrace")
							|| c.type.equals("geoshape")) {
						
						geomIdx = newColIdx;
						cols.append("ST_AsGeoJSON(" + c.name + ") ");
						geomType = c.type;
						newColIdx++;
					
					} else if(c.type.equals("image") || c.type.equals("audio") || c.type.equals("video")) {
							cols.append("'" + urlprefix + "' || " + c.name + " as " + c.name);
				
					} else if(c.name.equals("prikey") || c.name.equals("parkey") 
							|| c.name.equals("_bad") || c.name.equals("_bad_reason")) {
						cols.append(tName + "." + c.name + " as " +  c.name);
						
					}  else {
						cols.append(c.name);
						
					}
					
					colNames.add(c.humanName);
					columns.put(c.humanName);
					newColIdx++;
				}
				
				/*
				 * Add the server side calculations
				 */
				String sqlSSC = "select ssc.name, ssc.function, ssc.units from ssc ssc, form f " +
						" where f.f_id = ssc.f_id " +
						" and f.table_name = ? " +
						" order by ssc.id;";
				pstmtSSC = sd.prepareStatement(sqlSSC);	 
				pstmtSSC.setString(1, tName);
				log.info("SQL Get SSC: " + pstmtSSC.toString());
				resultSet = pstmtSSC.executeQuery();
				while(resultSet.next()) {
					String sscName = resultSet.getString(1);
					String sscFn = resultSet.getString(2);
					String sscUnits = resultSet.getString(3);

					if(geomType != null) {
						if(sscFn.equals("area")) {
							String colName = sscName + " (" + sscUnits + ")";
							if(newColIdx != 0 ) {cols.append(",");}
							cols.append("ST_Area(geography(the_geom), true)");
							if(sscUnits.equals("hectares")) {
								cols.append(" / 10000");
							}
							cols.append(" as \"" + colName + "\"");
							columns.put(colName);
							newColIdx++;
							sscList.add(colName);
						} else if (sscFn.equals("length")) {
							String colName = sscName + " (" + sscUnits + ")";
							if(newColIdx != 0 ) {cols.append(",");}
							if(geomType.equals("geopolygon") || geomType.equals("geoshape")) {
								cols.append("ST_Length(geography(the_geom), true)");
							} else {
								cols.append("ST_Length(geography(the_geom), true)");
							}
							if(sscUnits.equals("km")) {
								cols.append(" / 1000");
							}
							cols.append(" as \"" + colName + "\"");
							columns.put(colName);
							newColIdx++;
							sscList.add(colName);
						} else {
							log.info("Invalid SSC function: " + sscFn);
						}
					}

				}
				
				String sqlFilter = "";
				if(start_key > 0) {
					sqlFilter = tName + ".prikey > " +  start_key;
					if(!bBad) {
						sqlFilter += " AND _bad = 'false'";
					}
				} else {
					if(!bBad) {
						sqlFilter = " _bad = 'false'";
					}
				}
				
				 /*
				  * Get the where clause passed by the client
				  *  This may reference columns in a different table
				  */
				Filter filter = null;
				if(sFilter != null) {
					Type type = new TypeToken<Filter>(){}.getType();
					Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
					filter = gson.fromJson(sFilter, type);
					
					QuestionInfo fQ = new QuestionInfo(sId, filter.qId, sd);	
					tables.add(fQ.getTableName(), fQ.getFId(), fQ.getParentFId());
					log.info("Filter expression: " + fQ.getFilterExpression(filter.value, null));
					
					if(sqlFilter.length() > 0) {
						sqlFilter += " and " + fQ.getFilterExpression(filter.value, null);
					} else {
						sqlFilter = fQ.getFilterExpression(filter.value, null);
					}
				}
				
				/*
				 * Add row filtering performed by RBAC
				 */
				RoleManager rm = new RoleManager();
				ArrayList<SqlFrag> rfArray = rm.getSurveyRowFilter(sd, sId, request.getRemoteUser());
				String rfString = "";
				if(rfArray.size() > 0) {
					for(SqlFrag rf : rfArray) {
						if(rf.columns.size() > 0) {
							for(int i = 0; i < rf.columns.size(); i++) {
								int rqId = GeneralUtilityMethods.getQuestionIdFromName(sd, sId, rf.columns.get(i));
								QuestionInfo fRbac = new QuestionInfo(sId, rqId, sd);
								tables.add(fRbac.getTableName(), fRbac.getFId(), fRbac.getParentFId());
							}
							if(rfString.length() > 0) {
								rfString += " or";
							}
							rfString += " (" + rf.sql.toString() + ")";
							hasRbacRowFilter = true;
						}
					}
					if(sqlFilter.length() > 0) {
						sqlFilter += " and " + "(" + rfString + ")";
					} else {
						sqlFilter = "(" + rfString + ")";
					}
				}
				
				/*
				 * Get the date question used to set start and end date
				 */
				// Get date column information
				QuestionInfo date = null;
				if(dateId != 0 && (startDate != null || endDate != null)) {
					date = new QuestionInfo(sId, dateId, sd, false, "", urlprefix);	// Not interested in label any language will do
					tables.add(date.getTableName(), date.getFId(), date.getParentFId());
					log.info("Date name: " + date.getColumnName() + " Date Table: " + date.getTableName());
				}
				
				// Add any tables required to complete the join
				tables.addIntermediateTables(sd);
				
				jTotals.put("rec_limit", rec_limit);
				String sqlLimit = "";
				if(rec_limit > 0) {
					sqlLimit = "LIMIT " + rec_limit;
				}
				StringBuffer sql2 = new StringBuffer("select ");
				sql2.append(cols);
				sql2.append(" from ");
				sql2.append(tables.getTablesSQL());
				
				String sqlTableJoin = tables.getTableJoinSQL();
				boolean doneWhere = false;
				String whereClause = "";
				if(sqlTableJoin.trim().length() > 0) {
					whereClause += " where " + sqlTableJoin;
					doneWhere = true;
				} 
				
				if(sqlFilter.trim().length() > 0) {
					if(doneWhere) {
						whereClause += " and ";
					} else {
						whereClause += " where ";
						doneWhere = true;
					}
					whereClause += sqlFilter;
				}
				if(date != null) {
					String sqlRestrictToDateRange = GeneralUtilityMethods.getDateRange(startDate, endDate, date.getTableName(), date.getColumnName());
					if(sqlRestrictToDateRange.trim().length() > 0) {
						if(doneWhere) {
							whereClause += " and ";
						} else {
							whereClause += " where ";
							doneWhere = true;
						}
						whereClause += sqlRestrictToDateRange;
					}
				}
				sql2.append(whereClause);
				sql2.append(" order by " + tName + ".prikey " + sqlLimit +";");
				
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = connection.prepareStatement(sql2.toString());
				
				/*
				 * Set prepared statement values
				 */
				int attribIdx = 1;
				
				// RBAC row filter
				if(hasRbacRowFilter) {
					for(SqlFrag rf : rfArray) {
						for(int i = 0; i < rf.params.size(); i++) {
							SqlFragParam p = rf.params.get(i);
							if(p.type.equals("text")) {
								pstmt.setString(attribIdx++, p.sValue);
							} else if(p.type.equals("integer")) {
								pstmt.setInt(attribIdx++,  p.iValue);
							} else if(p.type.equals("double")) {
								pstmt.setDouble(attribIdx++,  p.dValue);
							}
						}
					}
				}
				
				// dates
				if(dateId != 0) {
					if(startDate != null) {
						pstmt.setDate(attribIdx++, startDate);
					}
					if(endDate != null) {
						pstmt.setDate(attribIdx++, endDate);
					}
				}
				
				// Request the data
				log.info("Get Item Data: " + pstmt.toString());
				resultSet = pstmt.executeQuery();
	
				JSONArray ja = new JSONArray();
				while (resultSet.next()) {
					JSONObject jr = new JSONObject();
					JSONObject jp = new JSONObject();
					JSONObject jg = null;
					
					jr.put("type", "Feature");

					for(int i = 0; i < colNames.size(); i++) {		
						
						if(i == geomIdx) {							
							// Add Geometry (assume one geometry type per table)
							String geomValue = resultSet.getString(i + 1);	
							if(geomValue != null) {	
								jg = new JSONObject(geomValue);
							}
						} else {
							
							//String name = rsMetaData.getColumnName(i);	
							String name = colNames.get(i);
							String value = resultSet.getString(i + 1);	
							if(value == null) {
								value = "";
							}
							
							if(name.equals("prikey")) {
								JSONArray prikeys = new JSONArray();
								jp.put("prikeys", prikeys);
								prikeys.put(resultSet.getString("prikey"));
								maxRec = resultSet.getInt("prikey");
							} else if(name.equals("Survey Name")) {
								// Get the display name
								String displayName = surveyNames.get(value);
								if(displayName == null && value.length() > 0) {
									displayName = GeneralUtilityMethods.getSurveyName(sd, Integer.parseInt(value));
									surveyNames.put(value, displayName);
								}
								jp.put(name, displayName);
							} else {
								jp.put(name, value);
							}
						}
					} 
					
					/*
					 * Get the server side calculates
					 */
					for(int i = 0; i < sscList.size(); i++) {
						String name = sscList.get(i);
						String value = resultSet.getString(name);	
						if(value == null) {
							value = "";
						}
						jp.put(name, value);
					}

					if(!bMustHaveGeom || jg != null) {
						jr.put("geometry", jg);
						jr.put("properties", jp);
						ja.put(jr);
					}
					recCount++;
				 }
				
				String maxRecordWhere = "";
				if(whereClause.equals("")) {
					maxRecordWhere = " where " + tName + ".prikey > " + maxRec;
				} else {
					maxRecordWhere = whereClause + " and " + tName + ".prikey > " + maxRec;
				}
				// Determine if there are more records to be returned
				sql = "SELECT count(*) FROM " + tables.getTablesSQL() + maxRecordWhere + ";";
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = connection.prepareStatement(sql);	
				
				// Apply the parameters again
				attribIdx = 1;
				// RBAC row filter
				if(hasRbacRowFilter) {
					for(SqlFrag rf : rfArray) {
						for(int i = 0; i < rf.params.size(); i++) {
							SqlFragParam p = rf.params.get(i);
							if(p.type.equals("text")) {
								pstmt.setString(attribIdx++, p.sValue);
							} else if(p.type.equals("integer")) {
								pstmt.setInt(attribIdx++,  p.iValue);
							} else if(p.type.equals("double")) {
								pstmt.setDouble(attribIdx++,  p.dValue);
							}
						}
					}
				}
				if(dateId != 0) {
					if(startDate != null) {
						pstmt.setDate(attribIdx++, startDate);
					}
					if(endDate != null) {
						pstmt.setDate(attribIdx++, endDate);
					}
				}
				
				log.info("Check for more records: " + pstmt.toString());
				resultSet = pstmt.executeQuery();
				if(resultSet.next()) {
					jTotals.put("more_recs", resultSet.getInt(1));
				}
				 jTotals.put("max_rec", maxRec);
				 jTotals.put("returned_count", recCount);
				 
				 jo.put("type", "FeatureCollection");
				 jo.put("features", ja);
				 jo.put("cols", columns);
				 jo.put("formName", formName);
				
			} catch (SQLException e) {
			    log.info("Did not get items for table - " + tName + ", Message=" + e.getMessage());
				String msg = e.getMessage();
				if(!msg.contains("does not exist") || msg.contains("column")) {	// Don't do a stack dump if the table did not exist that just means no one has submitted results yet
					log.log(Level.SEVERE,"SQL Error", e);
				}
			} catch (JSONException e) {
				log.log(Level.SEVERE,"JSON Error", e);
			} finally {
				
				try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
				try {if (pstmtSSC != null) {pstmtSSC.close();	}} catch (SQLException e) {	}
				try {if (pstmtFDetails != null) {pstmtFDetails.close();	}} catch (SQLException e) {	}
				
				SDDataSource.closeConnection("surveyKPI-Items", sd);
				ResultsDataSource.closeConnection("surveyKPI-Items", connection);	
			}
		}

		return jo.toString();
	}
	
	/*
	 * Update the settings
	 */
	@POST
	@Path("/bad/{key}")
	@Consumes("application/json")
	public Response toggleBad(@Context HttpServletRequest request,
			@PathParam("form") int fId,
			@PathParam("key") int key,
			@FormParam("value") boolean value,
			@FormParam("sId") int sId,
			@FormParam("reason") String reason
			) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Items");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation

		Connection cRel = null; 
		PreparedStatement pstmt = null;
		try {
			log.info("New toggle bad");
			cRel = ResultsDataSource.getConnection("surveyKPI-Items");
			
			// Get the table name
			String sql = "SELECT DISTINCT table_name, parentform FROM form f " +
					" where f.s_id = ?" + 
					" and f.f_id = ?;";
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setInt(2, fId);
			log.info("Get table name: " + pstmt.toString());
			
			ResultSet tableSet = pstmt.executeQuery();
			if(tableSet.next()) {
				String tName = tableSet.getString(1);
				int pId = tableSet.getInt(2);
				boolean isChild = pId > 0;
				UtilityMethodsEmail.markRecord(cRel, connectionSD, tName, value, reason, key, sId, fId, false, isChild);
			} else {
				throw new Exception("Could not get form id");
			}

			response = Response.ok().build();
				
		} catch (Exception e) {
			String msg = e.getMessage();
			if(msg.equals("Failed to update record")) {
				response = Response.status(Status.OK).entity("Record cannot be modified").build();
			} else {
				response = Response.serverError().build();
				log.log(Level.SEVERE,"Error", e);
			}
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-Items", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI-Items", cRel);
		}
		
		return response;
	}


}

