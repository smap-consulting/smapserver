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
import org.smap.sdal.model.Column;

import utilities.QuestionInfo;
import utilities.Tables;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns data for the passed in table name
 */
@Path("/items/{table}")
public class Items extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Items.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Items.class);
		return s;
	}
	
	/*
	 * JSON
	 * Usage /surveyKPI/items/{table}?geom=yes|no&feats=yes|no&mustHaveGeom=yes|no
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
			@PathParam("table") String tName, 
			@QueryParam("geom") String geom,		
			@QueryParam("feats") String feats,		
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
		boolean bFeats = true;
		boolean bMustHaveGeom = true;
		int maxRec = 0;
		int recCount = 0;

		ArrayList<String> colNames = new ArrayList<String> ();
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";	

		if(geom != null && geom.equals("no")) {
			bGeom = false;
		}
		if(feats != null && feats.equals("no")) {
			bFeats = false;
		}
		if(mustHaveGeom != null && mustHaveGeom.equals("no")) {
			bMustHaveGeom = false;
		}
		
		// Escape any quotes
		if(tName != null) {
			tName = tName.replace("'", "''"); 
		}
			
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Error: Can't find PostgreSQL JDBC Driver", e);
		    return "Error: Can't find PostgreSQL JDBC Driver";
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Items");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// Get the survey id
		String sql = "select s.s_id, s.ident FROM form f, survey s " + 
				" where s.s_id = f.s_id " +
				" and f.table_name = ?;";
		int sId = 0;

		try {
			PreparedStatement pstmtAuth = connectionSD.prepareStatement(sql);
			pstmtAuth.setString(1, tName);
			log.info("Authorisation: " + pstmtAuth.toString());
			
			ResultSet tableSet = pstmtAuth.executeQuery();
	
			if(tableSet.next()) {
				sId = tableSet.getInt(1);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error in Authorisation", e);
		}
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		log.info("Filter: " + sFilter);
		
		Tables tables = new Tables(sId);
		
		if(tName != null) {
			
			Connection connection = null;
			PreparedStatement pstmt = null;
			PreparedStatement pstmtSSC = null;
			PreparedStatement pstmtFDetails = null;
			PreparedStatement pstmtQuestions = null;
			PreparedStatement pstmtSelectMultiple = null;
			
			int fId = 0;
			int parent = 0;
			 
			try {
				
				// Prepare the statement to get the form details
				String sqlFDetails = "select f.f_id, f.parentform from question q, form f " +
					" where f.table_name = ?; ";
				pstmtFDetails = connectionSD.prepareStatement(sqlFDetails);
				
				// Connect to the results database
				connection = ResultsDataSource.getConnection("surveyKPI-Items");		
				int geomIdx = -1;
				
				JSONObject jTotals = new JSONObject();
				jo.put("totals", jTotals);
				jTotals.put("start_key", start_key);
				
				// Get the number of records
				sql = "SELECT count(*) FROM " + tName + ";";
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
				
				// Get the table details
				// Get the question type
				pstmtFDetails.setString(1, tName);
				ResultSet rsDetails = pstmtFDetails.executeQuery();
				if(rsDetails.next()) {
					
					fId = rsDetails.getInt(1);
					parent = rsDetails.getInt(2);
					
					tables.add(tName, fId, parent);
				}
				
				// Get column names for select multiple questions
				String sqlSelectMultiple = "select distinct o.column_name, o.ovalue, o.seq from option o, question q "
						+ "where o.l_id = q.l_id "
						+ "and q.q_id = ? "
						+ "and o.externalfile = ? "
						+ "and o.published = 'true' "
						+ "order by o.seq;";
				pstmtSelectMultiple = connectionSD.prepareStatement(sqlSelectMultiple);
				
				// Get the columns
				String sqlCols = "select qname, qtype, column_name, q_id from question "
						+ "where f_id = ? "
						+ "and source is not null "
						+ "and published = 'true' "
						+ "order by seq";
				pstmtQuestions = connectionSD.prepareStatement(sqlCols);
				pstmtQuestions.setInt(1, fId);
				
				ArrayList<Column> columnList = GeneralUtilityMethods.getColumnsInForm(
						connectionSD,
						connection,
						parent,
						fId,
						tName,
						false,	// Don't include Read only
						true,	// Include parent key
						true,	// Include "bad"
						true	// Include instanceId
						);		
				
				/*
				log.info("Get questions for form: " + pstmtQuestions.toString());
				resultSet = pstmtQuestions.executeQuery();
				*/
				// Construct a new query that retrieves a geometry object as geoJson
				StringBuffer cols = new StringBuffer("");
				int newColIdx = 0;
				JSONArray columns = new JSONArray();
				ArrayList<String> sscList = new ArrayList<String> ();
				
				for(Column c : columnList) {
					if(newColIdx > 0) {
						cols.append(",");
					}
					if(bGeom && c.qType.equals("geopoint") || c.qType.equals("geopolygon") 
							|| c.qType.equals("geolinestring") || c.qType.equals("geotrace")
							|| c.qType.equals("geoshape")) {
						
						geomIdx = newColIdx;
						cols.append("ST_AsGeoJSON(" + c.name + ") ");
						
						newColIdx++;
					
					} else if(c.qType.equals("image") || c.qType.equals("audio") || c.qType.equals("video")) {
							cols.append("'" + urlprefix + "' || " + c.name + " as " + c.name);
				
					} else {
						cols.append(c.name);
					}
					
					
					colNames.add(c.humanName);
					columns.put(c.humanName);
					newColIdx++;
				}
				
				// Add default columns
				/*
				cols.append("prikey, parkey, _bad, _bad_reason");
				
				columns.put("prikey");
				colNames.add("prikey");
				newColIdx++;
				
				columns.put("Parent Key");
				colNames.add("parkey");
				newColIdx++;
				
				columns.put("Bad");
				colNames.add("_bad");
				newColIdx++;
				
				columns.put("Bad Reason");
				colNames.add("_bad_reason");
				newColIdx++;
				*/
				
				/*
				if(parent == 0) {
					cols.append(",_user");
					columns.put("User");
					colNames.add("_user");
					newColIdx++;
					
					if(GeneralUtilityMethods.columnType(connection, tName, "_version") != null) {
						cols.append(",_version");
						columns.put("Version");
						colNames.add("_version");
						newColIdx++;
					}
					
					if(GeneralUtilityMethods.columnType(connection, tName, "_complete") != null) {
						cols.append(",_complete");
						columns.put("Complete");
						colNames.add("_complete");
						newColIdx++;
					}
				}
				*/
				/*
				while(resultSet.next()) {
					
					String name = resultSet.getString(1);
					String type = resultSet.getString(2);
					String colname = tName + "." +  resultSet.getString(3);
					int qId = resultSet.getInt(4);
					
					if(name.trim().toLowerCase().equals("instanceid")) {
						name = "instanceid";
					}
					if(bGeom && type.equals("geopoint") || type.equals("geopolygon") || type.equals("geolinestring") || type.equals("geotrace")
							|| type.equals("geoshape")) {
						
						geomIdx = newColIdx;
						cols.append(", ST_AsGeoJSON(" + colname + ") ");
						
						newColIdx++;
						columns.put(name);
						colNames.add(name);
						
					} else if(bFeats && type.equals("select")) {
						
						boolean external = GeneralUtilityMethods.hasExternalChoices(connectionSD, qId);
						
						pstmtSelectMultiple.setInt(1, qId);
						pstmtSelectMultiple.setBoolean(2, external);
						
						ResultSet rsMultiples = pstmtSelectMultiple.executeQuery();
						while (rsMultiples.next()) {
							String mult_name = name + " - " + rsMultiples.getString(2);
							cols.append("," + colname + "__" + rsMultiples.getString(1));
							columns.put(mult_name);
							colNames.add(mult_name);
							newColIdx++;
						}
					} else if(bFeats || name.equals("prikey")) {	// Always return the primary key
						cols.append(",");	
						if(type.equals("image") || type.equals("audio") || type.equals("video")) {
							cols.append("'" + urlprefix + "' || " + colname + " as " + name);
						} else {
							cols.append(colname);
						}
						newColIdx++;
						columns.put(name);
						colNames.add(name);
						
					}
					
				} 
				*/
				
				/*
				 * Add the server side calculations
				 */
				String sqlSSC = "select ssc.name, ssc.function from ssc ssc, form f " +
						" where f.f_id = ssc.f_id " +
						" and f.table_name = ? " +
						" order by ssc.id;";
				pstmtSSC = connectionSD.prepareStatement(sqlSSC);	 
				pstmtSSC.setString(1, tName);
				log.info("SQL Get SSC: " + pstmtSSC.toString());
				resultSet = pstmtSSC.executeQuery();
				while(resultSet.next()) {
					String sscName = resultSet.getString(1);
					String sscFn = resultSet.getString(2);

					if(sscFn.equals("area")) {
						String colName = sscName + " (sqm)";
						if(newColIdx != 0 ) {cols.append(",");}
						cols.append("ST_Area(geography(the_geom), true) as \"" + colName + "\"");
						columns.put(colName);
						newColIdx++;
						sscList.add(colName);
					} else if (sscFn.equals("length")) {
						String colName = sscName + " (m)";
						if(newColIdx != 0 ) {cols.append(",");}
						cols.append("ST_Length(geography(the_geom), true) as \"" + colName +"\"");
						columns.put(colName);
						newColIdx++;
						sscList.add(colName);
					} else {
						log.info("Invalid SSC function: " + sscFn);
					}

				}
				
				String sqlFilter = "";
				if(start_key > 0) {
					sqlFilter = "prikey > " +  start_key;
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
					
					QuestionInfo fQ = new QuestionInfo(sId, filter.qId, connectionSD);	
					tables.add(fQ.getTableName(), fQ.getFId(), fQ.getParentFId());
					log.info("Filter expression: " + fQ.getFilterExpression(filter.value, null));
					
					if(sqlFilter.length() > 0) {
						sqlFilter += " and " + fQ.getFilterExpression(filter.value, null);
					} else {
						sqlFilter = fQ.getFilterExpression(filter.value, null);
					}
				}
				
				/*
				 * Get the date question used to set start and end date
				 */
				// Get date column information
				QuestionInfo date = null;
				if(dateId != 0 && (startDate != null || endDate != null)) {
					date = new QuestionInfo(sId, dateId, connectionSD, false, "", urlprefix);	// Not interested in label any language will do
					tables.add(date.getTableName(), date.getFId(), date.getParentFId());
					log.info("Date name: " + date.getColumnName() + " Date Table: " + date.getTableName());
				}
				
				// Add any tables required to complete the join
				tables.addIntermediateTables(connectionSD);
				
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
				sql2.append(" order by prikey " + sqlLimit +";");
				
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = connection.prepareStatement(sql2.toString());
				
				int attribIdx = 1;
				if(dateId != 0) {
					if(startDate != null) {
						pstmt.setDate(attribIdx++, startDate);
					}
					if(endDate != null) {
						pstmt.setDate(attribIdx++, endDate);
					}
				}
				
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
				log.info(sql);
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				pstmt = connection.prepareStatement(sql);	
				
				attribIdx = 1;
				if(dateId != 0) {
					if(startDate != null) {
						pstmt.setDate(attribIdx++, startDate);
					}
					if(endDate != null) {
						pstmt.setDate(attribIdx++, endDate);
					}
				}
				
				resultSet = pstmt.executeQuery();
				if(resultSet.next()) {
					jTotals.put("more_recs", resultSet.getInt(1));
				}
				 jTotals.put("max_rec", maxRec);
				 jTotals.put("returned_count", recCount);
				 
				 jo.put("type", "FeatureCollection");
				 jo.put("features", ja);
				 jo.put("cols", columns);
				
			} catch (SQLException e) {
			    log.info("Did not get items for table - " + tName + ", Message=" + e.getMessage());
				String msg = e.getMessage();
				if(!msg.contains("does not exist")) {	// Don't do a stack dump if the table did not exist that just means no one has submitted results yet
					log.log(Level.SEVERE,"SQL Error", e);
				}
			} catch (JSONException e) {
				log.log(Level.SEVERE,"JSON Error", e);
			} finally {
				
				try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
				try {if (pstmtSSC != null) {pstmtSSC.close();	}} catch (SQLException e) {	}
				try {if (pstmtFDetails != null) {pstmtFDetails.close();	}} catch (SQLException e) {	}
				try {if (pstmtQuestions != null) {pstmtQuestions.close();	}} catch (SQLException e) {	}
				try {if (pstmtSelectMultiple != null) {pstmtSelectMultiple.close();	}} catch (SQLException e) {	}
				
				try {
					if (connectionSD != null) {
						connectionSD.close();
						connectionSD = null;
					}
				} catch (SQLException e) {
					log.log(Level.SEVERE,"Error: Failed to close connection", e);
				}
				
				try {
					if (connection != null) {
						connection.close();
						connection = null;
					}
				} catch (SQLException e) {
					log.log(Level.SEVERE,"Error: Failed to close connection", e);
				}
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
			@PathParam("table") String tName,
			@PathParam("key") int key,
			@FormParam("value") boolean value,
			@FormParam("sId") int sId,
			@FormParam("reason") String reason
			) { 
		
		Response response = null;
		
		// Escape any quotes
		if(tName != null) {
			tName = tName.replace("'", "''"); 
		} 
		
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
			
			// Get the form id
			String sql = "SELECT DISTINCT f_id, parentform FROM form f " +
					" where f.s_id = ?" + 
					" and f.table_name = ?;";
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setString(2, tName);
			log.info(sql + " : " + sId + " : " + tName);
			
			ResultSet tableSet = pstmt.executeQuery();
			if(tableSet.next()) {
				int fId = tableSet.getInt(1);
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
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
			
			try {
				if (cRel != null) {
					cRel.close();
					cRel = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			}
		}
		
		return response;
	}


}

