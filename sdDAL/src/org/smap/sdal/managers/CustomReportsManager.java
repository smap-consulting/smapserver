package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.Response;

import org.smap.sdal.model.NameId;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.CustomReportItem;
import org.smap.sdal.model.LQAS;
import org.smap.sdal.model.TableColumn;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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

/*
 * Manage the table that stores details on tasks
 */
public class CustomReportsManager {

	private static Logger log = Logger.getLogger(CustomReportsManager.class.getName());

	/*
	 * Save a report to the database
	 */
	public void save(Connection sd, String reportName, String config, int oId, String type) throws Exception {

		String sql = "insert into custom_report (o_id, name, config, type) values (?, ?, ?, ?);";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, oId);
			pstmt.setString(2, reportName);
			pstmt.setString(3, config);
			pstmt.setString(4, type);

			log.info(pstmt.toString());
			pstmt.executeUpdate();

		} catch (SQLException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			throw (new Exception(e.getMessage()));
		} finally {
			try {
				pstmt.close();
			} catch (Exception e) {
			}
			;
		}
	}

	/*
	 * get a list of reports for a select list
	 */
	public ArrayList<CustomReportItem> getList(Connection sd, int oId, String type, boolean negateType)
			throws SQLException {

		ArrayList<CustomReportItem> reports = new ArrayList<CustomReportItem>();

		String sql1 = "select id, name, type from custom_report where o_id = ? ";
		String sql2 = "and type = ? ";
		String sql2b = "and type != ? ";
		String sql3 = "order by name asc";
		String sql4 = "and (type = 'oversight' or type = 'oversight1') ";
		String sql5 = "and type != 'oversight' and type != 'oversight1' ";

		PreparedStatement pstmt = null;

		log.info("Type is: " + type + " Negate type is: " + negateType);
		try {

			if (type != null) {
				if (negateType) {
					if(type.equals("oversight") || type.equals("oversight1")) {
						// Temporarily add for backward compatibility version 17.09
						pstmt = sd.prepareStatement(sql1 + sql5 + sql3);
					} else {
						pstmt = sd.prepareStatement(sql1 + sql2b + sql3);
					}
				} else {
					if(type.equals("oversight") || type.equals("oversight1")) {
						// Temporarily add for backward compatibility version 17.09
						pstmt = sd.prepareStatement(sql1 + sql4 + sql3);
					} else {
						pstmt = sd.prepareStatement(sql1 + sql2 + sql3);
					}
				}
			} else {
				pstmt = sd.prepareStatement(sql1 + sql3);
			}

			pstmt.setInt(1, oId);
			if (type != null && !type.equals("oversight") && !type.equals("oversight1")) {
				pstmt.setString(2, type);
			}

			log.info(pstmt.toString());
			ResultSet rs = pstmt.executeQuery();

			while (rs.next()) {
				CustomReportItem item = new CustomReportItem();
				item.id = rs.getInt(1);
				item.name = rs.getString(2);
				item.type = rs.getString(3);
				reports.add(item);
			}

		} finally {
			try {
				pstmt.close();
			} catch (Exception e) {
			}
			;
		}

		return reports;

	}

	/*
	 * Get a report from the database
	 */
	public ReportConfig get(Connection sd, int crId, int oId) throws Exception {

		ReportConfig config = null;
		String sql = null;
		if (oId > 0) {
			sql = "select type, config from custom_report where id = ? and o_id = ?";
		} else {
			sql = "select type, config from custom_report where id = ?"; // trusted organisation
		}
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, crId);
			if (oId > 0) {
				pstmt.setInt(2, oId);
			}

			log.info(pstmt.toString());
			pstmt.executeQuery();

			String configString = null;
			String reportType = null;
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				reportType = rs.getString(1);
				configString = rs.getString(2);
				Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();

				Type type = null;
				if (reportType.equals("oversight")) { // Legacy report structure containing only columns
					type = new TypeToken<ArrayList<TableColumn>>() {
					}.getType();
					config = new ReportConfig();
					config.columns = gson.fromJson(configString, type);
				} else if (reportType.equals("oversight1")) {
					config = gson.fromJson(configString, ReportConfig.class);
				} else {
					throw new Exception("Invalid report type: " + reportType);
				}

			}

		} catch (SQLException e) {
			throw (new Exception(e.getMessage()));
		} finally {
			try {
				pstmt.close();
			} catch (Exception e) {
			}
			;
		}

		return config;
	}

	/*
	 * Get a report from the database
	 */
	public LQAS getLQASReport(Connection sd, int crId) throws Exception {

		LQAS config = null;
		String sql = "select config from custom_report where id = ?";
		PreparedStatement pstmt = null;

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, crId);

			log.info(pstmt.toString());
			pstmt.executeQuery();

			String configString = null;
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				configString = rs.getString(1);
				Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
				config = gson.fromJson(configString, LQAS.class);
			}

		} catch (SQLException e) {
			throw (new Exception(e.getMessage()));
		} finally {
			try {
				pstmt.close();
			} catch (Exception e) {
			}
			;
		}

		return config;
	}

	/*
	 * Delete a report
	 */
	public void delete(Connection sd, int oId, int id, ResourceBundle localisation) throws Exception {

		String sqlManaged = "select s.s_id, s.display_name, p.name " + "from survey s, project p "
				+ "where s.managed_id = ? " + "and s.p_id = p.id " + "and s.deleted = false " + "and p.o_id = ?";
		PreparedStatement pstmtManaged = null;

		String sql = "delete from custom_report " + "where id = ? " + "and o_id = ?";
		PreparedStatement pstmt = null;

		try {

			ResultSet resultSet = null;

			pstmtManaged = sd.prepareStatement(sqlManaged);
			pstmtManaged.setInt(1, id);
			pstmtManaged.setInt(2, oId);
			log.info("Delete report, check managed: " + pstmtManaged.toString());

			resultSet = pstmtManaged.executeQuery();
			boolean inUse = false;
			String formsUsingReport = "";
			while (resultSet.next()) {
				inUse = true;
				if (formsUsingReport.length() > 0) {
					formsUsingReport += ", ";
				}
				formsUsingReport += "\"" + resultSet.getString(2) + "\" ";
				formsUsingReport += localisation.getString("mf_ip") + " \"" + resultSet.getString(3) + "\"";
			}
			if (inUse) {
				throw new ApplicationException(localisation.getString("mf_riu") + ": " + formsUsingReport + ". "
						+ localisation.getString("mf_ul")); // Report is in use
			}
			resultSet.close();

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, id);
			pstmt.setInt(2, oId);
			log.info("Delete report: " + pstmt.toString());
			pstmt.executeUpdate();

		} finally {

			try {
				if (pstmtManaged != null) {
					pstmtManaged.close();
				}
			} catch (SQLException e) {
			}
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}

		}
	}

}
