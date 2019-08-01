package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.constants.SmapServerMeta;
import org.smap.sdal.model.ChartDefn;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.ManagedFormItem;
import org.smap.sdal.model.MapLayer;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.model.SurveySettingsDefn;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.TableColumnConfig;
import org.smap.sdal.model.TableColumnMarkup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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

/*
 * Managed Forms
 */
public class SurveyViewManager {
	
	private static Logger log =
			 Logger.getLogger(SurveyViewManager.class.getName());
	
	private ResourceBundle localisation = null;
	String tz;
	
	public static String ASSIGNED_COLUMN = "_assigned";
	
	public SurveyViewManager(ResourceBundle l, String tz) {
		localisation = l;
		if(tz == null) {
			tz = "UTC";
		}
		this.tz = tz;
	}
	
	/*
	 * Get the Managed Form Configuration
	 */
	public SurveyViewDefn getSurveyView(
			Connection sd, 
			Connection cResults,
			int uId,
			SurveySettingsDefn ssd,
			int sId,
			int managedId,
			String uIdent,
			int oId,
			boolean superUser,
			String groupSurvey) throws SQLException, Exception  {
		
		SurveyViewDefn svd = new SurveyViewDefn(ssd, sId, managedId, 0);
		
		ArrayList<TableColumnConfig> configColumns = new ArrayList<TableColumnConfig> ();
		String language = "none";
		
		// SQL to get view details
		String sql = "select view, map_view, chart_view "
				+ "from survey_view sv, user_view uv "
				+ "where sv.id = uv.v_id "
				+ "and sv.id = ? "
				+ "and uv.u_id = ? ";
		PreparedStatement pstmt = null;
		
		ResultSet rs = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {

			// Get the survey view
			/*
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, viewId);
			pstmt.setInt(2, uId);
			log.info("Get view defn: " + pstmt.toString());
			
			rs = pstmt.executeQuery();
			if(rs.next()) {
				String sView = rs.getString(1);
				String sMapView = rs.getString(2);
				String sChartView = rs.getString(3);
				// TODO get chart view
				
				if(sView != null) {
					Type type = new TypeToken<ArrayList<TableColumnConfig>>(){}.getType();	
					try {
						configColumns = gson.fromJson(sView, type);
					} catch (Exception e) {
						log.log(Level.SEVERE,"Error: ", e);
					}
				} 
				
				if(sMapView != null) {
					Type type = new TypeToken<ArrayList<MapLayer>>(){}.getType();	
					try {
						svd.layers = gson.fromJson(sMapView, type);
					} catch (Exception e) {
						log.log(Level.SEVERE,"Error: ", e);
						svd.layers = new ArrayList<MapLayer> ();		// If there is an error its likely that the structure of the config file has been changed and we should start from scratch
					}
				} else {
					svd.layers = new ArrayList<MapLayer> ();
				}
				
				if(sChartView != null) {
					Type type = new TypeToken<ArrayList<ChartDefn>>(){}.getType();	
					try {
						svd.charts = gson.fromJson(sChartView, type);
					} catch (Exception e) {
						log.log(Level.SEVERE,"Error: ", e);
						svd.charts = new ArrayList<ChartDefn> ();		// If there is an error its likely that the structure of the config file has been changed and we should start from scratch
					}
				} else {
					svd.charts = new ArrayList<ChartDefn> ();
				}
				
			}
			*/
			
			// Add the main form columns to the Survey View Definition
			String surveyIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
			populateSvd(sd, 
					cResults, 
					svd,
					configColumns,
					true,			// Is main survey
					language,
					sId,
					surveyIdent,
					uIdent,
					superUser);
			
			/*
			 * Add the managed form columns and configuration - Deprecate
			 */
			if(managedId > 0) {
				getDataProcessingConfig(sd, managedId, svd, configColumns, oId);
			}
			
			// Add the managed form columns from the group survey
			if(groupSurvey != null) {
				int groupSurveyId = GeneralUtilityMethods.getSurveyId(sd, groupSurvey);
				populateSvd(sd, 
						cResults, 
						svd,
						configColumns,
						false,			// Is main survey
						language,
						groupSurveyId,
						groupSurvey,
						uIdent,
						superUser);
			}
		
				
		} catch (SQLException e) {
		    throw e;			
		} catch (Exception e) {
			throw e;
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}
		
		return svd;

	}
	
	/*
	 * 
	 */
	public void populateSvd(
			Connection sd, 
			Connection cResults, 
			SurveyViewDefn svd,
			ArrayList<TableColumnConfig> configColumns,
			boolean isMain,
			String language,
			int sId,
			String surveyIdent,
			String uIdent,
			boolean superUser) throws Exception {
		
		Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId); // Get formId of top level form and its table name
		if(isMain) {
			svd.tableName = f.tableName;
		}
		ArrayList<TableColumn> columnList = GeneralUtilityMethods.getColumnsInForm(
				sd,
				cResults,
				localisation,
				language,
				sId,
				surveyIdent,
				uIdent,
				null,	// roles to apply
				0,
				f.id,
				f.tableName,
				false,	// Don't include Read only
				isMain,	// Include parent key
				isMain,	// Include "bad"
				isMain,	// Include instanceId
				isMain,	// include prikey
				isMain,	// Include other meta data
				isMain,		// include preloads
				isMain,		// include instancename
				isMain,		// Survey duration
				superUser,
				false,		// HXL only include with XLS exports
				false,		// Don't include audit data
				tz,
				isMain		// mgmt - Only the main survey request should result in the addition of the mgmt columns
				);		
		
		
		/*
		 * Add any configuration settings
		 * Order the config according to the current survey definition and
		 * Add any new columns that may have been added to the survey since the configuration was created
		 */			
		for(int i = 0; i < columnList.size(); i++) {
			TableColumn c = columnList.get(i);
			if(keepThis(c.column_name, isMain)) {
				TableColumn tc = new TableColumn(c.column_name, c.question_name, c.displayName);
				if(configColumns.size() == 0) {	// If a view was not passed then there are no config columns so get everything
					tc.hide = hideDefault(c.displayName);
				}
				tc.mgmt = !isMain;
				tc.filter = c.filter;
				tc.type = convertToTableColumnType(c.type);
				tc.l_id = c.l_id;
				if(!isMain) {
					tc.readonly = false;
				}
				
				for(int j = 0; j < configColumns.size(); j++) {
					TableColumnConfig tcConfig = configColumns.get(j);
					if(tcConfig.name.equals(tc.column_name)) {
						tc.hide = tcConfig.hide;
						tc.barcode = tcConfig.barcode;
						tc.filterValue = tcConfig.filterValue;
						tc.chart_type = tcConfig.chart_type;
						tc.width = tcConfig.width;
						break;
					}
				}
				
				if(tc.column_name.equals("the_geom")) {
					tc.displayName = "_geolocation";
				}

				// Add markup for assigned column
				if(tc.column_name.equals(ASSIGNED_COLUMN)) {
					tc.markup = new ArrayList<> ();
					tc.markup.add(new TableColumnMarkup(uIdent, "bg-info"));		// Blue
					tc.markup.add(new TableColumnMarkup("", "bg-warning"));		// Yellow
				}
				
				if(tc.include) {					
					svd.columns.add(tc);
					
					if(isMain) {
						svd.mainColumnNames.put(tc.column_name, svd.columns.size() - 1);
					} else {
						// remove columns from the data form that are in the group survey form
						Integer idx = svd.mainColumnNames.get(tc.column_name);
						if(idx != null) {
							svd.columns.remove(idx.intValue());
						}
					}
					
				}
			}
		}
		
		/*
		 * Add the choice lists TODO - What is this used for?  Are choice lists also required for the group survey?
		 */
		svd.choiceLists = GeneralUtilityMethods.getChoicesInForm(sd, sId, f.id);
	}
	
	/*
	 * Convet types from Survey Definition to Table Column Tyoe
	 */
	private String convertToTableColumnType(String type) {
		if(type != null) {
			if(type.equals("string")) {
				return "text";
			}
		}
		return type;
	}
	
	/*
	 * Save a survey view
	 */
	public int save(Connection sd, 
			int uId,
			int viewId,
			int sId, 
			int managedId, 
			int queryId, 
			String view, 
			String mapView, 
			String chartView) throws Exception {
		
		String sqlUpdateView = "update survey_view set view = ? ";
		String sqlUpdateMap = "update survey_view set map_view = ? ";
		String sqlUpdateChart = "update survey_view set chart_view = ? ";
		String sqlUpdate = "where id = ? "
				+ "and s_id = ? "
				+ "and m_id = ? "
				+ "and query_id = ?;";
		PreparedStatement pstmtUpdateView = null;
		
		String sqlInsert = "insert into survey_view "
				+ "(s_id, m_id, query_id, view, map_view, chart_view) "
				+ "values(?, ?, ?, ?, ?, ?);";
		PreparedStatement pstmtInsert = null;
		
		String sqlUserView = "insert into user_view "
				+ "(u_id, v_id, access) "
				+ "values(?, ?, 'owner');";
		PreparedStatement pstmtUserView = null;
		
		String sqlDefaultView = "insert into default_user_view "
				+ "(u_id, s_id, m_id, query_id, v_id) "
				+ "values(?, ?, ?, ?, ?);";
		PreparedStatement pstmtDefaultView = null;
		
		String sqlDeleteDefault = "delete from default_user_view "
				+ "where u_id = ? "
				+ "and s_id = ? "
				+ "and m_id = ? "
				+ "and query_id = ?";
		PreparedStatement pstmtDeleteDefault = null;
		
		/*
		 * Convert the input to java classes and then back to json to ensure it is well formed
		 */
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		
		// Table configuration
		if(view != null) {
			Type viewType = new TypeToken<ArrayList<TableColumnConfig>>(){}.getType();	
			ArrayList<TableColumnConfig> objView = gson.fromJson(view, viewType);
			view = gson.toJson(objView);
		}
		// Map View
		if(mapView != null) {
			Type viewType = new TypeToken<ArrayList<MapLayer>>(){}.getType();	
			ArrayList<MapLayer> objView = gson.fromJson(mapView, viewType);
			mapView = gson.toJson(objView);
		}
		// TODO Chart View

		try {
		
			int count = 0;
			if(viewId > 0) {
				if(view != null) {
					pstmtUpdateView = sd.prepareStatement(sqlUpdateView + sqlUpdate);
					pstmtUpdateView.setString(1, view);
				} else if(mapView != null) {
					pstmtUpdateView = sd.prepareStatement(sqlUpdateMap + sqlUpdate);
					pstmtUpdateView.setString(1, mapView);
				} else if(chartView != null) {
					pstmtUpdateView = sd.prepareStatement(sqlUpdateChart + sqlUpdate);
					pstmtUpdateView.setString(1, chartView);
				}
				
				pstmtUpdateView.setInt(2, viewId);
				pstmtUpdateView.setInt(3, sId);
				pstmtUpdateView.setInt(4, managedId);
				pstmtUpdateView.setInt(5, queryId);
				log.info("Updating survey view: " + pstmtUpdateView.toString());
				count = pstmtUpdateView.executeUpdate();
			}
			
			if(viewId == 0 || count == 0) {
				
				log.info("Set autocommit false");
				sd.setAutoCommit(false);
				
				pstmtInsert = sd.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
				pstmtInsert.setInt(1, sId);
				pstmtInsert.setInt(2, managedId);
				pstmtInsert.setInt(3, queryId);
				pstmtInsert.setString(4, view);
				pstmtInsert.setString(5, mapView);
				pstmtInsert.setString(6, chartView);
				log.info("Inserting survey view: " + pstmtInsert.toString());
				pstmtInsert.executeUpdate();
				
				ResultSet rs = pstmtInsert.getGeneratedKeys();
				if(rs.next()) {
					viewId = rs.getInt(1);
				} else {
					throw new Exception("Failed to insert view into survey_view table");
				}
				
				pstmtUserView = sd.prepareStatement(sqlUserView);
				pstmtUserView.setInt(1,uId);
				pstmtUserView.setInt(2,viewId);
				log.info("adding view to user view table: " + pstmtUserView.toString());
				pstmtUserView.executeUpdate();
				
				// Delete the old default view setting
				pstmtDeleteDefault = sd.prepareStatement(sqlDeleteDefault);
				pstmtDeleteDefault.setInt(1, uId);
				pstmtDeleteDefault.setInt(2, sId);
				pstmtDeleteDefault.setInt(3, managedId);
				pstmtDeleteDefault.setInt(4, queryId);
				log.info("adding view to default view table: " + pstmtDeleteDefault.toString());
				pstmtDeleteDefault.executeUpdate();
				
				// Set the view as the user's default
				pstmtDefaultView = sd.prepareStatement(sqlDefaultView);
				pstmtDefaultView.setInt(1, uId);
				pstmtDefaultView.setInt(2, sId);
				pstmtDefaultView.setInt(3, managedId);
				pstmtDefaultView.setInt(4, queryId);
				pstmtDefaultView.setInt(5, viewId);
				log.info("adding view to default view table: " + pstmtDefaultView.toString());
				pstmtDefaultView.executeUpdate();
				
				sd.commit();
				
				
			}
		} catch (Exception e) {
			try {sd.rollback();} catch (Exception ex) {}
			log.log(Level.SEVERE,"Error", e);
			throw new Exception(e.getMessage());
		} finally {
			log.info("Set autocommit true");
			try {sd.setAutoCommit(true);} catch (Exception e) {}
			try {if (pstmtUpdateView != null) {pstmtUpdateView.close();}} catch (Exception e) {}
			try {if (pstmtInsert != null) {pstmtInsert.close();}} catch (Exception e) {}
			try {if (pstmtUserView != null) {pstmtUserView.close();}} catch (Exception e) {}
			try {if (pstmtDefaultView != null) {pstmtDefaultView.close();}} catch (Exception e) {}
			try {if (pstmtDeleteDefault != null) {pstmtDeleteDefault.close();}} catch (Exception e) {}
		}
		return viewId;
	}
	
	/*
	 * Get the managed columns
	 */
	public void getDataProcessingConfig(Connection sd, int crId, 
			SurveyViewDefn svd, 
			ArrayList<TableColumnConfig> configColumns,
			int oId) throws Exception {
		
		CustomReportsManager crm = new CustomReportsManager ();
		ReportConfig config = crm.get(sd, crId, oId);
		
		// Get the managed form's parameters
		svd.parameters = config.settings;
		
		for(int i = 0; i < config.columns.size(); i++) {
			TableColumn tc = config.columns.get(i);

			tc.mgmt = true;
			if(configColumns != null) {
				for(int j = 0; j < configColumns.size(); j++) {
					TableColumnConfig tcConfig = configColumns.get(j);
					if(tcConfig.name.equals(tc.column_name)) {
						tc.hide = tcConfig.hide;
						tc.barcode = tcConfig.barcode;
						tc.filterValue = tcConfig.filterValue;
						break;
					}
				}
			}
			
			// remove columns from the data form that are in the configuration form
			for(int j = 0; j < svd.columns.size(); j++) {
				TableColumn fc = svd.columns.get(j);
				if(fc.column_name != null && fc.column_name.equals(tc.column_name)) {
					svd.columns.remove(j);
					break;
				}
			}
			
			// Add dynamic choice values such as users identified by role
			if(tc.choices != null) {
				ArrayList<KeyValue> newChoices = new ArrayList<KeyValue> ();
				for(KeyValue kv : tc.choices) {
					if(kv.isRole) {
						newChoices.addAll(GeneralUtilityMethods.getUsersWithRole(sd, oId, kv.k));
					} else {
						newChoices.add(kv);
					}
				}
				tc.choices = newChoices;
			}
			// Add the management column to the array of columns
			svd.columns.add(tc);
		}
		
	}
	
	
	/*
	 * Get a list of the surveys in a project and their management status
	 */
	public ArrayList<ManagedFormItem> getManagedForms(Connection sd, int pId) throws SQLException {
		
		ArrayList<ManagedFormItem> items = new ArrayList<ManagedFormItem> ();
		
		String sql = "select s.s_id, s.managed_id, s.display_name, cr.name "
				+ "from survey s "
				+ "left outer join custom_report cr "
				+ "on s.managed_id = cr.id "
				+ "where s.p_id = ? "
				+ "and s.deleted = false "
				+ "order by s.display_name";
		
		PreparedStatement pstmt = null;
		
		try {
			
			pstmt = sd.prepareStatement(sql);
			
			pstmt.setInt(1, pId);
			
			log.info(pstmt.toString());
			ResultSet rs = pstmt.executeQuery();
			
			while(rs.next()) {
				ManagedFormItem item = new ManagedFormItem();
				item.sId = rs.getInt(1);
				item.managedId = rs.getInt(2);
				item.surveyName = rs.getString(3);
				item.oversightName = rs.getString(4);
				item.managed = (item.managedId > 0);
				items.add(item);
			}
			
		} finally {
			try {pstmt.close();} catch(Exception e) {};
		}
		
		return items;
	}
	
	/*
	 * Identify any columns that should be dropped
	 */
	private boolean keepThis(String name, boolean isMain) {
		boolean keep = true;
		
		if(name.equals(SmapServerMeta.SURVEY_ID_NAME) ||
				name.equals("parkey") ||
				name.equals("_version") ||
				name.equals("_complete") ||
				name.equals("_location_trigger") ||
				name.equals("_device") ||
				name.equals("_bad") ||
				name.equals("_bad_reason")
				) {
			keep = false;
			
			// Instance id only returned once
			if(keep && !isMain && name.equals("instanceid")) {
				keep = false;
			}
		}
		return keep;
	}
		
	/*
	 * Set a default hide value
	 */
	private boolean hideDefault(String name) {
		boolean hide = false;
		
		if(name.equals(SmapServerMeta.SURVEY_ID_NAME) ||
				name.equals("User") ||
				name.equals("_scheduled_start") ||
				name.equals("Survey Notes") ||
				name.equals("Survey Duration") ||
				name.equals("_start") ||
				name.equals("decision_date") ||
				name.equals("programme") ||
				name.equals("project") ||
				name.equals("Instance Name") ||
				name.equals("instanceid") ||
				name.equals("the_geom") ||
				name.equals("_end") 
				) {
			hide = true;
		}
		
		return hide;
	}
}


