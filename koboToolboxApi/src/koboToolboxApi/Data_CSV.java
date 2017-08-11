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

import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

import org.codehaus.jettison.json.JSONArray;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.CustomReportsManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.TableDataManager;
import org.smap.sdal.model.TableColumn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*
 * Returns data for the passed in table name
 */
@Path("/v1/data.csv")
public class Data_CSV extends Application {

	Authorise a = null;

	private static Logger log = Logger.getLogger(Data_CSV.class.getName());

	LogManager lm = new LogManager(); // Application log

	// Tell class loader about the root classes. (needed as tomcat6 does not support
	// servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Data_CSV.class);
		return s;
	}

	public Data_CSV() {
		ArrayList<String> authorisations = new ArrayList<String>();
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}

	/*
	 * KoboToolBox API version 1 /data.csv CSV version
	 */
	@GET
	@Produces("text/csv")
	public void getDataCsv(@Context HttpServletRequest request, @QueryParam("filename") String filename,
			@Context HttpServletResponse response) {

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolBoxApi-getDataCSV");
		a.isAuthorised(sd, request.getRemoteUser());

		StringBuffer record = null;
		PrintWriter outWriter = null;

		if (filename == null) {
			filename = "forms.csv";
		}

		DataManager dm = new DataManager();
		try {
			ArrayList<DataEndPoint> data = dm.getDataEndPoints(sd, request, true);

			response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

			outWriter = response.getWriter();

			if (data.size() > 0) {
				for (int i = 0; i < data.size(); i++) {
					DataEndPoint dep = data.get(i);
					if (i == 0) {
						outWriter.print(dep.getCSVColumns() + "\n");
					}
					outWriter.print(dep.getCSVData() + "\n");

				}
			} else {
				outWriter.print("No Data");
			}

			outWriter.flush();
			outWriter.close();

		} catch (Exception e) {
			e.printStackTrace();
			try {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			} catch (Exception ex) {
			}
			;
		} finally {
			SDDataSource.closeConnection("koboToolBoxApi-getDataCSV", sd);
		}

	}

	/*
	 * KoboToolBox API version 1 /data
	 */
	@GET
	@Produces("text/csv")
	@Path("/{sId}")
	public void getDataRecords(@Context HttpServletRequest request, @Context HttpServletResponse response,
			@PathParam("sId") int sId, @QueryParam("start") int start, // Primary key to start from
			@QueryParam("limit") int limit, // Number of records to return
			@QueryParam("mgmt") boolean mgmt, @QueryParam("group") boolean group, // If set include a dummy group value
			// in the response, used by
			// duplicate query
			@QueryParam("sort") String sort, // Column Human Name to sort on
			@QueryParam("dirn") String dirn, // Sort direction, asc || desc
			@QueryParam("form") int fId, // Form id (optional only specify for a child form)
			@QueryParam("start_parkey") int start_parkey, // Parent key to start from
			@QueryParam("parkey") int parkey, // Parent key (optional, use to get records that correspond to a single
			// parent record)
			@QueryParam("hrk") String hrk, // Unique key (optional, use to restrict records to a specific hrk)
			@QueryParam("format") String format, // dt for datatables otherwise assume kobo
			@QueryParam("bad") String include_bad, // yes | only | none Include records marked as bad
			@QueryParam("filename") String filename, @QueryParam("audit") String audit_set) {

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get data records csv");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation

		lm.writeLog(sd, sId, request.getRemoteUser(), "view", "API CSV view");

		Connection cResults = ResultsDataSource.getConnection("koboToolboxApi - get data records csv");

		String sqlGetManagedId = "select managed_id from survey where s_id = ?";
		PreparedStatement pstmtGetManagedId = null;

		String sqlGetMainForm = "select f_id, table_name from form where s_id = ? and parentform = 0;";
		PreparedStatement pstmtGetMainForm = null;

		String sqlGetForm = "select parentform, table_name from form where s_id = ? and f_id = ?;";
		PreparedStatement pstmtGetForm = null;

		PreparedStatement pstmt = null;

		StringBuffer columnSelect = new StringBuffer();
		StringBuffer columnHeadings = new StringBuffer();
		StringBuffer record = null;
		PrintWriter outWriter = null;

		String table_name = null;
		int parentform = 0;
		int managedId = 0;
		boolean getParkey = false;
		ResultSet rs = null;

		if (sort != null && dirn == null) {
			dirn = "asc";
		}

		boolean audit = false;
		if (audit_set != null && audit_set.equals("yes")) {
			audit = true;
		}

		if (include_bad == null) {
			include_bad = "none";
		}

		boolean isDt = false;

		if (filename == null) {
			filename = "data.csv";
		}

		try {
			response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
			outWriter = response.getWriter();
			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);

			// Get the managed Id
			if (mgmt) {
				pstmtGetManagedId = sd.prepareStatement(sqlGetManagedId);
				pstmtGetManagedId.setInt(1, sId);
				rs = pstmtGetManagedId.executeQuery();
				if (rs.next()) {
					managedId = rs.getInt(1);
				}
				rs.close();
			}

			if (fId == 0) {
				pstmtGetMainForm = sd.prepareStatement(sqlGetMainForm);
				pstmtGetMainForm.setInt(1, sId);

				log.info("Getting main form: " + pstmtGetMainForm.toString());
				rs = pstmtGetMainForm.executeQuery();
				if (rs.next()) {
					fId = rs.getInt(1);
					table_name = rs.getString(2);
				}
				rs.close();
			} else {
				getParkey = true;
				pstmtGetForm = sd.prepareStatement(sqlGetForm);
				pstmtGetForm.setInt(1, sId);
				pstmtGetForm.setInt(2, fId);

				log.info("Getting specific form: " + pstmtGetForm.toString());
				rs = pstmtGetForm.executeQuery();
				if (rs.next()) {
					parentform = rs.getInt(1);
					table_name = rs.getString(2);
				}
				rs.close();
			}

			ArrayList<TableColumn> columns = GeneralUtilityMethods.getColumnsInForm(sd, cResults, sId,
					request.getRemoteUser(), parentform, fId, table_name, false, getParkey, // Include parent key if the
					// form is not the top level
					// form (fId is 0)
					(include_bad.equals("yes") || include_bad.equals("only")), true, // include instance id
					true, // include other meta data
					true, // include preloads
					true, // include instancename
					true, // include survey duration
					superUser, false, // TODO include HXL
					audit);

			if (mgmt) {
				CustomReportsManager crm = new CustomReportsManager();
				ArrayList<TableColumn> managedColumns = crm.get(sd, managedId, -1);
				columns.addAll(managedColumns);
			}

			boolean hasAudit = false;
			for (int i = 0; i < columns.size(); i++) {
				TableColumn c = columns.get(i);

				if (c.name.equals("_audit")) {
					hasAudit = true;
				}
				if (i > 0) {
					columnSelect.append(",");
					if (c.name.equals("_audit")) {
						// Column headings at end
					} else {
						columnHeadings.append(",");
					}
				}
				columnSelect.append(c.getSqlSelect(urlprefix));
				if (!c.name.equals("_audit")) {
					columnHeadings.append(c.humanName);
				}
			}

			System.out.println("Add audit columns: " + hasAudit);
			// Add the audit columns
			if(hasAudit) {
				for (TableColumn c : columns) {
					if (includeInAudit(c.name)) {
						columnHeadings.append(",");
						columnHeadings.append(c.humanName);
						columnHeadings.append(" ");
						columnHeadings.append("(time ms)");
					}
				}
			}
			columnHeadings.append("\n");
			outWriter.print(columnHeadings.toString());

			if (GeneralUtilityMethods.tableExists(cResults, table_name)) {

				TableDataManager tdm = new TableDataManager();
				JSONArray ja = null;

				pstmt = tdm.getPreparedStatement(sd, cResults, columns, urlprefix, sId, table_name, parkey, hrk,
						request.getRemoteUser(), sort, dirn, mgmt, group, isDt, start, limit, getParkey, start_parkey,
						superUser, false, // Return records greater than or equal to primary key
						include_bad);

				log.info("Get CSV data: " + pstmt.toString());
				HashMap<String, Integer> auditData = null;
				Gson gson = new GsonBuilder().disableHtmlEscaping().create();
				Type type = new TypeToken<HashMap<String, Integer>>() {
				}.getType();

				rs = pstmt.executeQuery();

				int index = 0;
				while (rs.next()) {

					if (limit > 0 && index >= limit) {
						break;
					}
					index++;

					record = new StringBuffer();

					// Add the standard data
					for (int i = 0; i < columns.size(); i++) {
						TableColumn c = columns.get(i);

						String val = rs.getString(i + 1);
						if (val == null) {
							val = "";
						}
						if (!c.name.equals("_audit")) {
							if (i > 0) {
								record.append(",");
							}
							record.append("\"" + val.replaceAll("\"", "\"\"") + "\"");
						} else {
							auditData = gson.fromJson(val, type);
						}
					}

					// Add the audit data
					if (hasAudit && auditData != null) {
						for (TableColumn c : columns) {
							if (includeInAudit(c.name)) {
								record.append(",");
								if(auditData.get(c.name) != null) {
									record.append(auditData.get(c.name));
								}
							}
						}
					}

					record.append("\n");
					outWriter.print(record.toString());

				}

				outWriter.flush();
				outWriter.close();
			}

			} catch (Exception e) {
				log.log(Level.SEVERE, "Exception", e);
				try {
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				} catch (Exception ex) {
				}
				;
			} finally {

				try {
					if (pstmt != null) {
						pstmt.close();
					}
				} catch (SQLException e) {
				}
				try {
					if (pstmtGetMainForm != null) {
						pstmtGetMainForm.close();
					}
				} catch (SQLException e) {
				}
				try {
					if (pstmtGetForm != null) {
						pstmtGetForm.close();
					}
				} catch (SQLException e) {
				}
				try {
					if (pstmtGetManagedId != null) {
						pstmtGetManagedId.close();
					}
				} catch (SQLException e) {
				}

				ResultsDataSource.closeConnection("koboToolboxApi - get data records csv", cResults);
				SDDataSource.closeConnection("koboToolboxApi - get data records csv", sd);
			}

		}
	
	private boolean includeInAudit(String name) {
		boolean include = true;
		
		if(name.startsWith("_")) {
			include  = false;
		} else if(name.equals("prikey") || name.equals("instanceid") || name.equals("instancename")) {
			include = false;
		}
		return include;
	}

	}

