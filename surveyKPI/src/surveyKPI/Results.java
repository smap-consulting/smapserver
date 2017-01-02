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
import javax.ws.rs.GET;
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
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.SurveyManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import utilities.FeatureInfo;
import utilities.OptionInfo;
import utilities.QuestionInfo;
import utilities.SurveyInfo;
import utilities.Tables;

import java.lang.reflect.Type;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Provides results of surveys
 * Accepts
 *   List of question ids to return data for
 *   Grouping question id (can be a geometry question)
 *    If geometry can include some additional parameters such as an external table to get the geometry from
 *   Order question id (can be a date question, in which case the data can be "played back")
 *   order
 *   constraints, ie "gender = male"
 */


@Path("/results/{sId}")
public class Results extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(Results.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	private class RecordValues {
		public String key;
		public double value;
		
		public RecordValues(String k, double v) {
			key = k;
			value = v;
		}
	}

	static DateFormat dfHour = new SimpleDateFormat("yyyy-MM-dd H");
	static DateFormat dfDay = new SimpleDateFormat("yyyy-MM-dd");
	static DateFormat dfMonth = new SimpleDateFormat("yyyy-MM");
	static DateFormat dfMonthOnly = new SimpleDateFormat("MM");
	static DateFormat dfYear = new SimpleDateFormat("yyyy");
	static long MS_IN_HOUR = 86400000 / 24;
	static long MS_IN_DAY = 86400000;
	static long MS_IN_WEEK = 86400000 * 7;
	
	// Return results for a question in geoJson
	@GET
	@Produces("application/json")
	public Response getResults(@Context HttpServletRequest request,
			@PathParam("sId") int sId,				// Survey Id
			@QueryParam("fn") String fn,			// function
			@QueryParam("dateId") int dateId,		// Id of question containing the date to sort by
			@QueryParam("groupId") int groupId,		// Id of question for grouping
			@QueryParam("group_t") String group_t,	// Type of group geometry("internal || contains" || "name") non geometry("normal")
			@QueryParam("timeGroup") String timeGroup,	// none, day, week, month, year
			@QueryParam("qId") int qId,				// Question
			@QueryParam("qId_is_calc") boolean qId_is_calc,	// Server Side Calculate Question
			@QueryParam("lang") String lang,		// Language
			@QueryParam("geoTable") String geoTable,		// Name of external geo-table to get  geometries from
			@QueryParam("geomname") boolean geomname,	// If true return the name of the geometry instead of geojson
			@QueryParam("rId") int rId,				// Restrict results to a single record
			@QueryParam("startDate") Date startDate,
			@QueryParam("endDate") Date endDate,
			@QueryParam("filter") String sFilter
			) { 
	
		Response response = null;
		Date fromDate = null;			// Date of first record
		Date toDate = null;
		boolean hasGroup = false;
		boolean hasTimeGroup = false;
		boolean hasGeo = true;
		boolean externalGeom = false;
		QuestionInfo date = null;
		QuestionInfo group = null;
		QuestionInfo fQ = null;
		Tables tables = new Tables(sId);
		ArrayList<QuestionInfo> q = new ArrayList<QuestionInfo> ();
		HashMap<String, String> groupList = new HashMap<String, String> ();
		JSONArray results = new JSONArray();
		Connection dConnection = null;
		PreparedStatement pstmt = null;
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/";		
		
		// Get the Postgres driver
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Results");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false, superUser);	// Validate that the user can access this survey
		// End Authorisation
		
		if(groupId != 0) {
			hasGroup = true;
		}
		
		if(group_t != null) {
			group_t = group_t.replace("'", "''"); 
			if(group_t.equals("normal") || group_t.equals("string")) {
				hasGeo=false;
			}
		} else {
			group_t = "normal";	// Default to no geometry data and no group
			hasGeo = false;
		}
		
		if(geoTable != null) {
			geoTable = geoTable.replace("'", "''"); 
			externalGeom = true;
		}
		
		if(timeGroup != null) {
			timeGroup = timeGroup.replace("'", "''");
			if(timeGroup.equals("hour") || timeGroup.equals("day") || timeGroup.equals("week") || timeGroup.equals("month") || timeGroup.equals("year")) {
				hasTimeGroup = true;
			} else {
				timeGroup = "none";
			}
		} else {
			timeGroup = "none";
		}
					
		try {
			dConnection = ResultsDataSource.getConnection("surveyKPI-Results");

			/*
			 * Check that mandatory parameters have been set
			 */	
			if(lang == null) {
				throw new Exception("Language must be set &lang=xxx");
			}
			if(fn == null) {
				throw new Exception("Function must be set &fn=none || percent || average || count || total");
			}
			
			// Get date column information
			if(dateId != 0) {
				date = new QuestionInfo(sId, dateId, connectionSD, false, lang, urlprefix);
				q.add(date);
				tables.add(date.getTableName(), date.getFId(), date.getParentFId());
				log.info("Date name: " + date.getColumnName() + " Date Table: " + date.getTableName());
			}
			
			// Get group column information
			if(hasGroup) {
				group = new QuestionInfo(sId, groupId, connectionSD, hasGeo, lang, urlprefix);	
				q.add(group);
				tables.add(group.getTableName(), group.getFId(), group.getParentFId());
			}
			
			// Get the geo table data
			if(geoTable != null) {
				q.add(new QuestionInfo(geoTable, "the_geom", true, "the_geom"));
				q.add(new QuestionInfo(geoTable, "name", true, "name"));
				tables.add(geoTable, -1, -1);
			}
	
			/*
			 * Get Survey meta data
			 */
			SurveyInfo survey = new SurveyInfo(sId, connectionSD);
			
			/*
			 * Add the the main question to the array of questions
			 */
			QuestionInfo aQ = null;
			if(qId_is_calc) {
				aQ = new QuestionInfo(sId, qId, connectionSD, false, lang, qId_is_calc, urlprefix);
			} else {
				aQ = new QuestionInfo(sId, qId, connectionSD, false, lang, urlprefix);
			}
			q.add(aQ);
			tables.add(aQ.getTableName(), aQ.getFId(),  aQ.getParentFId());			
			
			lm.writeLog(connectionSD, sId, request.getRemoteUser(), "view", "View results for question " + aQ.getName());
			
			 // Get the filter
			Filter filter = null;
			String sqlFilter = "";
			if(sFilter != null) {
				Type type = new TypeToken<Filter>(){}.getType();
				Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
				filter = gson.fromJson(sFilter, type);

				fQ = new QuestionInfo(sId, filter.qId, connectionSD, hasGeo, lang, urlprefix);	
				q.add(fQ);
				tables.add(fQ.getTableName(), fQ.getFId(), fQ.getParentFId());
				sqlFilter = " and " + fQ.getFilterExpression(filter.value, null);
			}
				
			// Add any tables required to complete the join
			tables.addIntermediateTables(connectionSD);
			
			/*
			 * Create the sql statement
			 */	
			boolean doneWhere = false;
			String sqlSelect = getSelect(q, externalGeom);
			String sqlTables = tables.getTablesSQL();
			String sqlTableJoin = tables.getTableJoinSQL();
			String sqlGeom = null;
			String sqlNoBad = tables.getNoBadClause();
			String sqlRestrictToRecordId = restrictToRecordId(aQ, rId);
			String sqlRestrictToDateRange = "";
			if(date != null) {
				sqlRestrictToDateRange = GeneralUtilityMethods.getDateRange(startDate, endDate, date.getColumnName());
			} 
			if(externalGeom) {
				sqlGeom = getGeometryJoin(q);
			}
	
			String sql = "select " + sqlSelect + " from " + sqlTables;
			if(sqlTableJoin.trim().length() > 0) {
				sql += " where " + sqlTableJoin;
				doneWhere = true;
			}
			if(sqlGeom != null) {
				if(doneWhere) {
					sql += " and ";
				} else {
					sql += " where ";
					doneWhere = true;
				}
				sql += sqlGeom;
			}
			if(doneWhere) {
				sql += " and ";
			} else {
				sql += " where ";
			}
			sql += sqlNoBad;
			sql += sqlRestrictToRecordId;
			if(sqlRestrictToDateRange.trim().length() > 0) {
				sql += " and ";
			}
			sql += sqlRestrictToDateRange;
			sql += sqlFilter;
			if(dateId != 0) {
				sql += " order by " + date.getTableName() + "." + date.getColumnName() + " asc;";
			} else {
				sql += " order by " + aQ.getTableName() + ".prikey asc;";
			}
				
			log.info("Get results select: " + sql);
			pstmt = dConnection.prepareStatement(sql);
			int attribIdx = 1;
			if(dateId != 0) {
				if(startDate != null) {
					pstmt.setDate(attribIdx++, startDate);
				}
				if(endDate != null) {
					pstmt.setTimestamp(attribIdx++, GeneralUtilityMethods.endOfDay(endDate));
				}
			}
			ResultSet resultSet = pstmt.executeQuery();

			/*
			 * Collect the data
			 */
			Map<String, FeatureInfo> featureHash = new HashMap<String, FeatureInfo>();
			//FeatureInfo defaultGroup = null;
			
			JSONObject featureCollection = new JSONObject();
			JSONArray featuresArray = new JSONArray();
			JSONArray columns = new JSONArray();
			JSONArray groups = new JSONArray();

			results.put(featureCollection);
			featureCollection.put("type", "FeatureCollection");
			featureCollection.put("source", "question");
			featureCollection.put("fn", fn);
			if(sFilter != null) {
				featureCollection.put("filter", sFilter);				
			}
			if(qId_is_calc) {
				featureCollection.put("question", aQ.getColumnName());
			} else {
				featureCollection.put("question", aQ.getLabel());
			}
			featureCollection.put("qtype", aQ.getType());
			featureCollection.put("survey", survey.getDisplayName());
			featureCollection.put("cols", columns);
			if(aQ.getUnits() != null) {
				featureCollection.put("units", aQ.getUnits());
			}

			if(group != null) {
				String groupLabel = group.getLabel();
				if(groupLabel == null) {
					groupLabel = group.getColumnName();
				}
				if(groupLabel!= null && groupLabel.trim().length() > 0) {
					featureCollection.put("group", groupLabel);
				}
			}
			featureCollection.put("features", featuresArray);
			
			if(hasTimeGroup) {
				featureCollection.put("interval", timeGroup);
			}

			/*
			 * Loop through each record
			 */
			int totalRecordCount = 0;
			int featureIndex = -1;
			boolean firstTime = true;
			long firstHour = 0;
			long firstDay = 0;
			long firstWeek = 0;
			long firstMonth = 0;
			long firstYear = 0;
			int timeIdx = 0;
			int maxTimeIdx = 0;
			while(resultSet.next()) {
				
				totalRecordCount++;
				
				// Find out which groups this record is a member of. 
				// A record can be part of multiple groups when the group by is a select multiple
				ArrayList<String> matchingGroups = new ArrayList<String>();
				String theGeom = null;
				String groupValue = null;		// The key to the group, ie the value of the question that is acting as the group
				String timeValue = "";
				// Get the time interval to add to the group
				if(hasTimeGroup) {
					Timestamp groupDate = resultSet.getTimestamp(date.getColumnName());
					if(groupDate != null) {
						long ms = groupDate.getTime();
						long day = Math.round(ms / MS_IN_DAY);
					
						if(timeGroup.equals("hour")) {
							long hour = Math.round(ms / MS_IN_HOUR);
							timeValue = dfHour.format(groupDate);
							if(totalRecordCount == 1) {
								firstHour = hour;
							}
							timeIdx = (int) (hour - firstHour);
							
						} else if(timeGroup.equals("day")) {
							timeValue = dfDay.format(groupDate);
							if(totalRecordCount == 1) {
								firstDay = day;
							}
							timeIdx = (int) (day - firstDay);
						} else if(timeGroup.equals("week")) {
							
							long week = Math.round(ms / MS_IN_WEEK);
							if(totalRecordCount == 1) {
								firstWeek = week;
							}
							timeIdx = (int) (week - firstWeek);
							timeValue = String.valueOf(timeIdx);
							
						} else if(timeGroup.equals("month")) {
							
							long month = Long.parseLong(dfYear.format(groupDate)) * 12 + Long.parseLong(dfMonthOnly.format(groupDate));
							if(totalRecordCount == 1) {
								firstMonth = month;
							}
							timeIdx = (int) (month - firstMonth);
							timeValue = dfMonth.format(groupDate);
							
						} else if(timeGroup.equals("year")) {
							long year = Long.parseLong(dfYear.format(groupDate));
							if(totalRecordCount == 1) {
								firstYear = year;
							}
							timeIdx = (int) (year - firstYear);
							timeValue = dfYear.format(groupDate);
						}
					}
				}
				
				if(hasGroup) {
					
					if(group.getType().equals("select")) {
						ArrayList<OptionInfo> options = group.getOptions();
						for(int i = 0; i < options.size(); i++) {
							OptionInfo anOption = options.get(i);
							groupValue = anOption.getColumnName();
							if(groupValue != null && groupValue.trim().length() > 0) {
								String oResult = resultSet.getString(groupValue);
								if(oResult.equals("1")) {
									matchingGroups.add(timeValue + groupValue);
								}
							}
						}
					} else if(group.getType().equals("select1")) {
						groupValue = resultSet.getString(group.getColumnName());	// TODO add table names to cater for non unique names
						if(groupValue != null && groupValue.trim().length() > 0) {
							matchingGroups.add(timeValue + groupValue);
						}
					} else if(isGeom(group.getType())) {
						theGeom = resultSet.getString("the_geom");
						if(externalGeom) {
							groupValue = resultSet.getString(geoTable + "_name");
						} else {
							String table = aQ.getTableName();			
							groupValue = table.substring(table.indexOf('_') + 1) + " " + totalRecordCount;
						}
						if(groupValue != null && groupValue.trim().length() > 0) {
							matchingGroups.add(timeValue + groupValue);
						}					
					} else if(group.getType().equals("string") || group.getType().equals("int") || group.getType().equals("calculate")) {
						groupValue = resultSet.getString(group.getColumnName());
						if(groupValue != null  && groupValue.trim().length() > 0) {
							matchingGroups.add(timeValue + groupValue);
						}
					} else {
						log.info("Unknown group type:" +  group.getType());
					}
				} else {
					// Create a dummy group
					if(fn.equals("none")) {
						matchingGroups.add(timeValue + String.valueOf(totalRecordCount));	// A dummy group for every record
					} else {
						matchingGroups.add(timeValue + "dummy");							// A single dummy group
					}
				}
					

				// Create any Group / Feature objects that this record belongs to and that have not already been created
				for(int i = 0; i < matchingGroups.size(); i++) {
					groupValue = matchingGroups.get(i);
						
					/*
					 * If the group hasn't been created yet then create it
					 */
					if(featureHash.get(groupValue) == null) {
						featureIndex++;
							
						JSONObject feature = new JSONObject();
						JSONObject featureProps = new JSONObject();
						JSONArray prikeys = new JSONArray();
						featuresArray.put(feature);
							
						if(hasGeo && theGeom != null && !geomname) {
							JSONObject jg = new JSONObject(theGeom);
							feature.put("geometry", jg);
						}
						feature.put("properties", featureProps);
						feature.put("type", "feature");
						if(timeValue != null  && timeValue.trim().length() > 0) {
							featureProps.put("period", timeValue);
							featureProps.put("timeIdx", timeIdx);
							maxTimeIdx = timeIdx;
						}
						featureProps.put("prikeys", prikeys);
							
						String groupName = null;
						String groupLabel = null;
						String nonPeriodGroupValue = null;
						
						if(timeValue != null  && timeValue.trim().length() > 0) {
							nonPeriodGroupValue = groupValue.substring(timeValue.length());
						} else {
							 nonPeriodGroupValue = groupValue;
						}
						if(group != null) {
							if(isGeom(group.getType()) && !externalGeom) {
								groupName = "geom" + featureIndex;	// Make up a dummy geometry name
							} else if(group.getType().startsWith("select")) {
								groupName = nonPeriodGroupValue;
								groupLabel = group.getOptionLabelForName(groupName);
							} else {
								groupName = nonPeriodGroupValue;
								groupLabel = nonPeriodGroupValue;
							}
							featureProps.put("group_name", groupName);
							featureProps.put("group_label", groupLabel);
							featureProps.put("group_value", nonPeriodGroupValue);
							groupList.put(groupLabel, groupLabel);	// add to array of all groups
						}	
							
						// Keep info on this feature in a hash table so it can be reused 
						FeatureInfo info = new FeatureInfo();
						info.featureProps = featureProps;
						featureHash.put(groupValue, info);
		
					} 
				}
				
				
				/*
				 * Add this record to all the groups that it matches 
				 */
				for(int i = 0; i < matchingGroups.size(); i++) {
					groupValue = matchingGroups.get(i);
					
					FeatureInfo groupInfo = featureHash.get(groupValue);
					groupInfo.incRecordCount();
					ArrayList<RecordValues> values = new ArrayList<RecordValues> ();

					// Add the primary key		
					JSONArray prikeys = (JSONArray) groupInfo.featureProps.get("prikeys");
					prikeys.put(resultSet.getString("prikey"));
					
					
					if(!aQ.isGeom()) {	// Ignore external geometry tables
						
						if(aQ.getType().startsWith("select")) {
							
							ArrayList<OptionInfo> options = aQ.getOptions();
							for(int k = 0; k < options.size(); k++) {
								String optionValue = null;
								OptionInfo oi = options.get(k);
								String value = null;
								if(aQ.getType().equals("select")) {
									value = resultSet.getString(oi.getColumnName());
								} else {
									optionValue = resultSet.getString(aQ.getColumnName());
									if(optionValue != null && optionValue.equals(oi.getValue())) {
										value = "1";
									} else {
										value = "0";
									}
								}
								
								// Add to the values array
								if(fn.equals("none")) {
									if(firstTime) {		// Store the column names in an array
										columns.put(oi.getLabel());   // Store the column names in an array
									}
									if(hasGroup) {
										// Grouped results without an aggregating function - put in an array
										try {
											JSONArray propArray = (JSONArray) groupInfo.featureProps.get(oi.getLabel());
										} catch (JSONException e) {
											JSONArray propArray = new JSONArray();
											groupInfo.featureProps.put(oi.getLabel(), propArray);
										}
										JSONArray propArray = (JSONArray) groupInfo.featureProps.get(oi.getLabel());
										propArray.put(value);
									} else {
										groupInfo.featureProps.put(oi.getLabel(), value);
										
									}
								} else {
									int aVal = 0;
									try {
										aVal = Integer.parseInt(value);	
									} catch (Exception e) {
									}
									values.add(new RecordValues(oi.getLabel(), aVal));
								}		
								
								if(optionValue != null) {
									groupInfo.featureProps.put("oname", optionValue);
								}

							}
							firstTime = false;
						
						} else {
							String value = resultSet.getString(aQ.getColumnName());
							
							if(fn.equals("none")) {
								if(firstTime) {		// Store the column names in an array
									columns.put(aQ.getColumnName());
								}
								if(hasGroup) {
									// Grouped results without an aggregating function - put in an array
									try {
										JSONArray propArray = (JSONArray) groupInfo.featureProps.get(aQ.getColumnName());
									} catch (JSONException e) {
										JSONArray propArray = new JSONArray();
										groupInfo.featureProps.put(aQ.getColumnName(), propArray);
									}
									JSONArray propArray = (JSONArray) groupInfo.featureProps.get(aQ.getColumnName());
									propArray.put(value);
								} else {
									groupInfo.featureProps.put(aQ.getColumnName(), value);
								}
								
							} else {	
								double aVal = 0.0;
								try {
									aVal = Double.parseDouble(value);
								} catch (Exception e) {
									
								}
								values.add(new RecordValues(aQ.getColumnName(), aVal ));
							}
						}
						firstTime = false;
					}
					
					for (int j = 0; j < values.size(); j++) {
					    String key = values.get(j).key;
					    double value = values.get(j).value;
						groupInfo.addItem(key, value);
					}
					

				}
				
				// If this is the first record then set the fromDate
				if(dateId != 0) {
					if(totalRecordCount == 1) {
						fromDate = resultSet.getDate(date.getColumnName());
					} else {
						toDate = resultSet.getDate(date.getColumnName());
					}
				}

			}
			
			/*
			 * Add the group array
			 */
			featureCollection.put("groups", groups);
			for(String gName : groupList.values()) {
				groups.put(gName);
			}
			
			/*
			 * Add from to dates to JSON output
			 */
			if(date != null) {
				featureCollection.put("date_question", date.getColumnName());
			}
			DateFormat df = DateFormat.getDateInstance();
			if(fromDate != null) {
				featureCollection.put("start", df.format(fromDate));
			}
			if(toDate != null) {
				featureCollection.put("end", df.format(toDate));
			}
			if(hasTimeGroup) {
				featureCollection.put("maxTimeIdx", maxTimeIdx);
			}
			
			/*
			 * If an aggregating function was used then add the aggregates to the results
			 */
			firstTime = true;
			if(!fn.equals("none")) {
				for (FeatureInfo fi : featureHash.values()) {		
				    //fi.featureProps.put("recordCount", fi.getRecordCount());
				    fi.addTotalsToJSONObject(fi.featureProps, fn, columns, firstTime);
				    firstTime = false;
				}
				
			}
			
			response = Response.ok(results.toString()).build();
				
				
		} catch (SQLException e) {
		    log.info("Message=" + e.getMessage());
		    String msg = e.getMessage();
			if(!msg.contains("does not exist")) {	// Don't do a stack dump if the table did not exist that just means no one has submitted results yet
				log.log(Level.SEVERE,"SQL Error", e);
				response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
			} else {
				response = Response.status(Status.NOT_FOUND).entity(msg).build();
			}

		} catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Results", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI-Results", dConnection);
		}


		return response;

	}
	
	private boolean isGeom(String type) {
		boolean geom = false;
		
		if(type != null) {
			if(type.equals("geopoint") || 
					type.equals("geopolygon") || 
					type.equals("geolinestring") ||
					type.equals("geoshape") ||
					type.equals("geotrace")) {
				geom = true;
			}
		}
		return geom;
	}
	
	
	/*
	 * Returns the SQL fragment that makes up the select
	 */
	private String getSelect(ArrayList<QuestionInfo> q, boolean externalGeom) {
		String sqlFrag = "";
		
		log.info("getSelect length is:" + q.size());
		int count = 0;
		for(int i = 0; i < q.size(); i++) {
			QuestionInfo aQuestion = q.get(i);
			if(externalGeom && isGeom(aQuestion.getType()) )  {
				// Skip the internal geometry question if geometry is being sourced from an external table
			} else {
				if(count++ > 0) {
					sqlFrag += ",";
				}
				sqlFrag += aQuestion.getSelect();
			}
		}
		return sqlFrag;
	}
	


	/*
	 * Returns the SQL fragment that joins geometry tables
	 */
	private String getGeometryJoin(ArrayList<QuestionInfo> q) {
		String sqlFrag = null;
		String geomInternalTable = null;
		String geomExternalTable = null;
		
		for(int i = 0; i < q.size(); i++) {
			QuestionInfo aQuestion = q.get(i);
			if(isGeom(aQuestion.getType())) {
				geomInternalTable = aQuestion.getTableName();
			} else if(aQuestion.isGeom()) {
				geomExternalTable = aQuestion.getTableName();
			}
		}
		if(geomInternalTable != null && geomExternalTable != null) {
			sqlFrag = "ST_Within(" + geomInternalTable + ".the_geom, " + geomExternalTable + ".the_geom)"; 
		}
		return sqlFrag;
	}
	
	/*
	 * Returns the SQL fragment that restricts results to a specific record
	 */
	private String restrictToRecordId(QuestionInfo q, int rId) {
		String sqlFrag = "";
		
		if(rId > 0) {
			sqlFrag += " and " + q.getTableName() + ".prikey = " + rId;
		}
		
		return sqlFrag;
	}

}

