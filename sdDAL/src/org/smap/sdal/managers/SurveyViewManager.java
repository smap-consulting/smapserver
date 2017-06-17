package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.ChartDefn;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.ManagedFormItem;
import org.smap.sdal.model.MapLayer;
import org.smap.sdal.model.TableColumn;
import org.smap.sdal.model.TableColumnConfig;

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
	
	/*
	 * Get the Managed Form Configuration
	 */
	public SurveyViewDefn getSurveyView(
			Connection sd, 
			Connection cResults,
			int uId,
			int viewId,
			int sId,
			int managedId,
			String uIdent,
			int oId,
			boolean superUser) throws SQLException, Exception  {
		
		SurveyViewDefn svd = new SurveyViewDefn(viewId, sId, managedId, 0);		// Todo Query id
		
		ArrayList<TableColumnConfig> configColumns = new ArrayList<TableColumnConfig> ();
		
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
			
			Form f = GeneralUtilityMethods.getTopLevelForm(sd, sId); // Get formId of top level form and its table name
			ArrayList<TableColumn> columnList = GeneralUtilityMethods.getColumnsInForm(
					sd,
					cResults,
					sId,
					uIdent,
					0,
					f.id,
					f.tableName,
					false,	// Don't include Read only
					true,	// Include parent key
					true,	// Include "bad"
					true,	// Include instanceId
					true,	// Include other meta data
					true,		// include preloads
					true,		// include instancename
					true,		// Survey duration
					superUser,
					false		// HXL only include with XLS exports
					);		
			
			
			/*
			 * Add any configuration settings
			 * Order the config according to the current survey definition and
			 * Add any new columns that may have been added to the survey since the configuration was created
			 */			
			for(int i = 0; i < columnList.size(); i++) {
				TableColumn c = columnList.get(i);
				if(keepThis(c.name)) {
					TableColumn tc = new TableColumn(c.name, c.humanName);
					tc.hide = hideDefault(c.humanName);
					tc.filter = c.filter;
					tc.type = c.type;
					tc.l_id = c.l_id;
					for(int j = 0; j < configColumns.size(); j++) {
						TableColumnConfig tcConfig = configColumns.get(j);
						if(tcConfig.name.equals(tc.name)) {
							tc.hide = tcConfig.hide;
							tc.barcode = tcConfig.barcode;
							tc.filterValue = tcConfig.filterValue;
							tc.chart_type = tcConfig.chart_type;
							tc.width = tcConfig.width;
							break;
						}
					}
					
					if(tc.name.equals("the_geom")) {
						tc.name = "_geolocation";
					}
					if(tc.include) {
						svd.columns.add(tc);
					}
				}
			}
			
			/*
			 * Add the managed form columns and configuration
			 */
			if(managedId > 0) {
				getDataProcessingConfig(sd, managedId, svd.columns, configColumns, oId);
			}
			
			/*
			 * Add the choice lists
			 */
			svd.choiceLists = GeneralUtilityMethods.getChoicesInForm(sd, sId, f.id);
		
				
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
			ArrayList<TableColumn> formColumns, 
			ArrayList<TableColumnConfig> configColumns,
			int oId) throws Exception {
		
		CustomReportsManager crm = new CustomReportsManager ();
		ArrayList<TableColumn> managedColumns = crm.get(sd, crId, oId);
		for(int i = 0; i < managedColumns.size(); i++) {
			TableColumn tc = managedColumns.get(i);

			tc.mgmt = true;
			if(configColumns != null) {
				for(int j = 0; j < configColumns.size(); j++) {
					TableColumnConfig tcConfig = configColumns.get(j);
					if(tcConfig.name.equals(tc.name)) {
						tc.hide = tcConfig.hide;
						tc.barcode = tcConfig.barcode;
						tc.filterValue = tcConfig.filterValue;
						break;
					}
				}
			}
			
			// remove columns from the data form that are in the configuration form
			for(int j = 0; j < formColumns.size(); j++) {
				TableColumn fc = formColumns.get(j);
				if(fc.name.equals(tc.name)) {
					formColumns.remove(j);
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
			formColumns.add(tc);
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
	 * Get default view id
	 */
	public int getDefaultView(Connection sd, int uId, int sId, int managedId, int queryId) throws SQLException {
		int viewId = 0;
		
		String sqlGetDefault = "select v_id from default_user_view "
				+ "where u_id = ? "
				+ "and s_id = ? "
				+ "and query_id = ? "
				+ "and m_id = ?";
		PreparedStatement pstmtGetDefault = null;
		
		try {
			
			pstmtGetDefault = sd.prepareStatement(sqlGetDefault);
			
			pstmtGetDefault.setInt(1, uId);
			pstmtGetDefault.setInt(2, sId);
			pstmtGetDefault.setInt(3, queryId);
			pstmtGetDefault.setInt(4, managedId);
			
			log.info("Get default view: " + pstmtGetDefault.toString());
			ResultSet rs = pstmtGetDefault.executeQuery();
			if(rs.next()) {
				viewId = rs.getInt(1);
			}
		} finally {
			if(pstmtGetDefault != null) { try{pstmtGetDefault.close();}catch(Exception e) {}}
		}
		
		return viewId;
	}
	
	/*
	 * Identify any columns that should be dropped
	 */
	private boolean keepThis(String name) {
		boolean keep = true;
		
		if(name.equals("_s_id") ||
				name.equals("parkey") ||
				name.equals("_version") ||
				name.equals("_complete") ||
				name.equals("_location_trigger") ||
				name.equals("_device") ||
				name.equals("_bad") ||
				name.equals("_bad_reason") ||
				name.equals("instanceid")
				) {
			keep = false;
		}
		return keep;
	}
	
	
	
	/*
	 * Set a default hide value
	 */
	private boolean hideDefault(String name) {
		boolean hide = false;
		
		if(name.equals("_s_id") ||
				name.equals("User") ||
				name.equals("Upload Time") ||
				name.equals("Survey Notes") ||
				name.equals("_start") ||
				name.equals("decision_date") ||
				name.equals("programme") ||
				name.equals("project") ||
				name.equals("instanceName") ||
				name.equals("_end") 
				) {
			hide = true;
		}
		
		return hide;
	}
	

	

	
}


