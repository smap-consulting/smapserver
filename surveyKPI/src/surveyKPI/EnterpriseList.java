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
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.CsvTableManager;
import org.smap.sdal.managers.OrganisationManager;
import org.smap.sdal.model.DeviceSettings;
import org.smap.sdal.model.Enterprise;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.Project;
import org.smap.sdal.model.Role;
import org.smap.sdal.model.SensitiveData;
import org.smap.sdal.model.User;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Returns a list of all projects that are in the same organisation as the user making the request
 */
@Path("/enterpriseList")
public class EnterpriseList extends Application {

	Authorise a = null;

	private static Logger log =
			Logger.getLogger(EnterpriseList.class.getName());

	public EnterpriseList() {

		a = new Authorise(null, Authorise.ENTERPRISE);

	}

	@GET
	@Produces("application/json")
	public Response getEnterprises(@Context HttpServletRequest request,
			@QueryParam("tz") String tz) { 

		Response response = null;
		String connectionString = "surveyKPI-EnterpriseList-getEnterprises";

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation

		tz = (tz == null) ? "UTC" : tz;

		PreparedStatement pstmt = null;
		ArrayList<Enterprise> enterprises = new ArrayList<Enterprise> ();

		try {
			String sql = null;
			ResultSet resultSet = null;

			/*
			 * Get the organisation
			 */
			sql = "select id, "
					+ "name, "
					+ "changed_by, "
					+ "timezone(?, changed_ts) as changed_ts "
					+ "from enterprise "
					+ "order by name asc;";			

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, tz);
			log.info("Get enterprises: " + pstmt.toString());
			resultSet = pstmt.executeQuery();

			while(resultSet.next()) {
				Enterprise e = new Enterprise();
				e.id = resultSet.getInt("id");
				e.name = resultSet.getString("name");
				e.changed_by = resultSet.getString("changed_by");
				e.changed_ts = resultSet.getTimestamp("changed_ts");

				enterprises.add(e);
			}

			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(enterprises);
			response = Response.ok(resp).build();


		} catch (Exception e) {

			log.log(Level.SEVERE,"Error: ", e);
			response = Response.serverError().entity(e.getMessage()).build();

		} finally {
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {}
			SDDataSource.closeConnection(connectionString, sd);
		}

		return response;
	}

	/*
	 * Update the enterprise details or create a new enterprise
	 */
	@POST
	public Response updateEnterprise(
			@Context HttpServletRequest request,
			@FormParam("data") String data) throws Exception { 

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-OrganisationList-updateOrganisation");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation

		Response response = null;

		String sqlCreate = "insert into enterprise (name, changed_by, changed_ts) values (?,?,now())";
		String sqlUpdate = "update enterprise "
				+ "set name = ?, "
				+ "changed_by = ?,"
				+ " changed_ts = now() "
				+ "where id = ?";
		PreparedStatement pstmt = null;

		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		Enterprise enterprise = gson.fromJson(data, Enterprise.class);
		try {
			if(enterprise.id == -1) {	// New enterprise		
				pstmt = sd.prepareStatement(sqlCreate);
			} else {						// Existing enterprise				
				pstmt = sd.prepareStatement(sqlUpdate);
			}

			pstmt.setString(1, enterprise.name);
			pstmt.setString(2, request.getRemoteUser());
			if(enterprise.id > -1) {
				pstmt.setInt(3, enterprise.id);
			}
			log.info("Update enterprise: " + pstmt.toString());
			pstmt.executeUpdate();
			
			response = Response.ok().build();

		} catch (SQLException e) {
			String state = e.getSQLState();
			if(state.startsWith("23")) {
				response = Response.status(Status.CONFLICT).entity(e.getMessage()).build();
			} else {
				log.log(Level.SEVERE,"Error: ", e);
				response = Response.serverError().entity(e.getMessage()).build();
			}

		} finally {
			if(pstmt != null) try {pstmt.close();} catch (Exception e) {}
			SDDataSource.closeConnection("surveyKPI-OrganisationList-updateOrganisation", sd);
		}

		return response;
	}

	/*
	 * Delete an enterprise
	 */
	@DELETE
	@Consumes("application/json")
	public Response delEnterprise(@Context HttpServletRequest request, 
			@FormParam("data") String data) { 

		Response response = null;

		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-OrganisationList-delOrganisation");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation

		Type type = new TypeToken<ArrayList<Enterprise>>(){}.getType();		
		ArrayList<Enterprise> enterprises = new Gson().fromJson(data, type);

		PreparedStatement pstmt = null;
		PreparedStatement pstmtDrop = null;
		try {	

			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			String sql = null;
			ResultSet resultSet = null;
			sd.setAutoCommit(false);

			for(Enterprise e : enterprises) {
				
				/*
				 * Ensure that there are no undeleted organisations in this enterprise
				 */
				sql = "select count(*) "
						+ "from organisation  " 
						+ "where e_id = ? ";

				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, e.id);
				resultSet = pstmt.executeQuery();
				if(resultSet.next()) {
					int count = resultSet.getInt(1);
					if(count > 0) {
						log.info("Count of undeleted oganisations:" + count);
						String msg = localisation.getString("msg_undel_ents");
						msg = msg.replace("%s1", e.name);
						throw new Exception(msg);
					}
				} else {
					throw new Exception("Error getting project count");
				}

				sql = "delete from enterprise e "
						+ "where e.id = ?";			

				if(pstmt != null) try{pstmt.close();}catch(Exception ex) {}
				pstmt = sd.prepareStatement(sql);
				pstmt.setInt(1, e.id);
				log.info("SQL: " + sql + ":" + e.id);
				pstmt.executeUpdate();

			}

			response = Response.ok().build();
			sd.commit();

		} catch (SQLException e) {
			String state = e.getSQLState();
			log.info("Delete organisation: sql state:" + state);
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);
			try { sd.rollback();} catch (Exception ex){log.log(Level.SEVERE,"", ex);}

		} catch (Exception ex) {
			log.info(ex.getMessage());
			response = Response.serverError().entity(ex.getMessage()).build();

			try{	sd.rollback();} catch(Exception e2) {}

		} finally {

			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmtDrop != null) {pstmtDrop.close();}} catch (SQLException e) {}

			SDDataSource.closeConnection("surveyKPI-OrganisationList-delOrganisation", sd);
		}

		return response;
	}

}

