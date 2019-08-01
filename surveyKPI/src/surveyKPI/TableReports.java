package surveyKPI;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.FormParam;

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

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import org.apache.commons.codec.binary.Base64;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.PDFTableManager;
import org.smap.sdal.managers.SurveySettingsManager;
import org.smap.sdal.managers.SurveyViewManager;
import org.smap.sdal.model.ChartData;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.SurveySettingsDefn;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import utilities.XLSReportsManager;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Path("/tables")
public class TableReports extends Application {

	private static Logger log =
			 Logger.getLogger(TableReports.class.getName());
	
	Authorise a = null;
	
	LogManager lm = new LogManager();		// Application log

	public TableReports() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.VIEW_DATA);
		a = new Authorise(authorisations, null);
	}
	
	private class Chart {
		public String title;
		public String image;
		public String description;
		public String filePath;
		public String entry;	// A unique name for the chart
	}
	
	@POST
	@Path("/generate")
	public void generate(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@FormParam("sId") int sId,
			@FormParam("managedId") int managedId,
			@FormParam("data") String data,
			@FormParam("charts") String charts,
			@FormParam("format") String format,
			@FormParam("title") String title,
			@FormParam("project") String project,
			@FormParam("chartdata") String chartData,
			@FormParam("settings") String settingsString,
			@FormParam("tz") String tz
			) throws Exception { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-tables");
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		// End Authorisation
		
		boolean isXLS = format.toLowerCase().equals("xls") || format.toLowerCase().equals("xlsx");
		boolean isPdf = format.toLowerCase().equals("pdf");
		boolean isImage = format.toLowerCase().equals("image");
		if(title == null) {
			title = "Results";
		}
		if(project == null) {
			project = "Project";
		}
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-tables");
		
		if(tz == null) {
			tz = "UTC";
		}
		
		try {
			
			// Localisation
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			int uId = GeneralUtilityMethods.getUserId(sd, request.getRemoteUser());
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			// Get columns
			SurveyViewManager svm = new SurveyViewManager(localisation, tz);
			SurveySettingsManager ssm = new SurveySettingsManager(localisation, tz);
			// Get the default view	
			
			String sIdent = GeneralUtilityMethods.getSurveyIdent(sd, sId);
			SurveySettingsDefn ssd = ssm.getSurveySettings(sd, uId, sIdent);
			SurveyViewDefn mfc = svm.getSurveyView(sd, cResults, uId, ssd, sId, managedId, 
					request.getRemoteUser(), oId, superUser, null);	// TODO add support for group survey
			
			// Convert data to an array
			ArrayList<ArrayList<KeyValue>> dArray = null;
			if(data != null) {
				Type type = new TypeToken<ArrayList<ArrayList<KeyValue>>>(){}.getType();		
				dArray = new Gson().fromJson(data, type);
			}
			
			// Convert charts to an array
			ArrayList<Chart> chartArray = null;
			if(charts != null) {
				Type type = new TypeToken<ArrayList<Chart>>(){}.getType();		
				chartArray = new Gson().fromJson(charts, type);
			}
			
			// Convert chartData string to an object array
			ArrayList<ChartData> chartDataArray = null;
			if(chartData != null) {
				Type type = new TypeToken<ArrayList<ChartData>>(){}.getType();		
				chartDataArray = new Gson().fromJson(chartData, type);
			}
			
			// Convert settings into an array of key value pairs
			ArrayList<KeyValue> settings = null;
			if(settingsString != null) {
				Type type = new TypeToken<ArrayList<KeyValue>>(){}.getType();		
				settings = new Gson().fromJson(settingsString, type);
			}
			
			if(isXLS) {
				XLSReportsManager xm = new XLSReportsManager(format);
				xm.createXLSReportsFile(response.getOutputStream(), dArray, chartDataArray, settings, mfc, localisation, tz);
			} else if(isPdf) {
				String basePath = GeneralUtilityMethods.getBasePath(request);
				PDFTableManager pm = new PDFTableManager();
				pm.createPdf(
						sd,
						response.getOutputStream(), 
						dArray, 
						mfc, 
						localisation, 
						tz, 
						false,	 // TBD set landscape and paper size from client
						request.getRemoteUser(),
						basePath,
						title,
						project);
						
			} else if(isImage) {
				if(charts != null && chartArray.size() > 0) {
					
					/*
					 * 1. Create the target folder
					 */
					String basePath = GeneralUtilityMethods.getBasePath(request);
					String filePath = basePath + "/temp/" + String.valueOf(UUID.randomUUID());	// Use a random sequence to keep survey name unique
					File folder = new File(filePath);
					folder.mkdir();
					
					/*
					 * Save the charts in the folder
					 */
					for(int i = 0; i < chartArray.size(); i++) {
						try { 
							Chart chart = chartArray.get(i);
							String imgData = chart.image.substring(chart.image.indexOf(",") + 1);
							byte[] b = Base64.decodeBase64(imgData);
							BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(b));
							if(bufferedImage == null) {
								log.info("Image is null");
							} 
							chart.entry = chart.title + i + ".png";
							chart.filePath = filePath + "/" + chart.entry;
							ImageIO.write(bufferedImage, "png", new File(chart.filePath));
						} catch(Exception e) {
							log.log(Level.SEVERE, "Error: Converting data url", e);
						}
					}
					
					/*
					 * Return the zip file
					 */
					ZipOutputStream zos = new ZipOutputStream(response.getOutputStream());
					byte[] buffer = new byte[1024];
					for(int i = 0; i < chartArray.size(); i++) {
						Chart chart = chartArray.get(i);
						ZipEntry ze= new ZipEntry(chart.entry);
			    			zos.putNextEntry(ze);
			    			FileInputStream in = new FileInputStream(chart.filePath);
			    		
			    			int len;
			    			while ((len = in.read(buffer)) > 0) {
			    				zos.write(buffer, 0, len);
			    			}

			    			in.close();
			    			zos.closeEntry();
					}
					zos.close();
					
				}
			}
			
		
		} finally {
			SDDataSource.closeConnection("surveyKPI-tables", sd);
			ResultsDataSource.closeConnection("surveyKPI-tables", cResults);
		}


	}
	
	

}

