package org.smap.sdal.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.model.ChangeItem;


public class MediaUtilities {
	
	private static Logger log =
			 Logger.getLogger(MediaUtilities.class.getName());
	
	/*
	 * Get the organisation id for the user
	 */
	static public int getOrganisationId(
			Connection sd, 
			String user) throws SQLException {
		
		int o_id = -1;
		
		String sqlGetOrgId = "select o_id " +
				" from users u " +
				" where u.ident = ?;";
		
		PreparedStatement pstmt = null;
		
		try {
		
			pstmt = sd.prepareStatement(sqlGetOrgId);
			pstmt.setString(1, user);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				o_id = rs.getInt(1);	
			}
			
		} catch(SQLException e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmt != null) { pstmt.close();}} catch (SQLException e) {}
		}
		
		return o_id;
	}
	
	/*
	 * Return true if this questions appearance means that choices come from an external file
	 */
	public static boolean isAppearanceExternalFile(String appearance) {
		if(appearance != null && appearance.toLowerCase().trim().startsWith("search(")) {
			return true;
		} else {
			return false;
		}
	}
		
	/*
	 * Get a list of options from an external file
	 */
	public static void getOptionsFromFile(
			Connection connectionSD,
			ArrayList<ChangeItem> ciList, 
			File csvFile,
			String csvFileName,
			String qName,
			int qId,
			String qType,
			String qAppearance) {
		
		BufferedReader br = null;
		try {
			FileReader reader = new FileReader(csvFile);
			br = new BufferedReader(reader);
			CSVParser parser = new CSVParser();
	       
			// Get Header
			String line = br.readLine();
			String cols [] = parser.parseLine(line);
			System.out.println("Header: " + line);
	       
			CSVFilter filter = new CSVFilter(cols, qAppearance);								// Get a filter
			ValueLabelCols vlc = getValueLabelCols(connectionSD, qId, qName, cols);		// Identify the columns in the CSV file that have the value and label

			while(line != null) {
				line = br.readLine();
				if(line != null) {
					String [] optionCols = parser.parseLine(line);
					if(filter.isIncluded(optionCols)) {
						System.out.println("        ## Include " + line);
		    		   
						ChangeItem c = new ChangeItem();
						c.qId = qId;
						c.name = qName;					// Add for logging
						c.fileName = csvFileName;		// Add for logging
						c.qType = qType;
						c.newVal = optionCols[vlc.label];
						c.key = optionCols[vlc.value];
		    		  
						ciList.add(c);
		    		   
					} else {
						System.out.println("        ## Ignore " + line);
					}
				}
			}
	       
		       // TODO delete all file options that were not in the latest file (file version number)
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {br.close();} catch(Exception e) {};
		}
	}
		
	/*
	 * Return the columns in a CSV file that have the value and label for the given question	
	 */
	public static ValueLabelCols getValueLabelCols(Connection connectionSD, int qId, String qDisplayName, String [] cols) throws Exception {
		
		ValueLabelCols vlc = new ValueLabelCols();
		
		/*
		 * Ignore language in the query, these values are codes and are (currently) independent of language
		 */
		PreparedStatement pstmt = null;
		String sql = "SELECT o.ovalue, t.value " +
				"from option o, translation t " +  		
				"where o.label_id = t.text_id " +
				"and o.q_id = ? " +
				"and externalfile ='false';";	
		
		try {
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1,  qId);
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String valueName = rs.getString(1);
				String labelName = rs.getString(2);
				System.out.println("Value column: " + valueName + " : " + labelName);
				
				vlc.value = -1;
				vlc.label = -1;
				for(int i = 0; i < cols.length; i++) {
					if(cols[i].toLowerCase().equals(valueName.toLowerCase())) {
						vlc.value = i;
					} else if(cols[i].toLowerCase().equals(labelName.toLowerCase())) {
						vlc.label = i;
					}
				}
			} else {
				throw new Exception("The names of the columns to use in this csv file "
						+ "have not been set in question " + qDisplayName);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			if (pstmt != null) try {pstmt.close();} catch(Exception e) {};
		}
		return vlc;
	}
	
	/*
	 * Get languages for a survey
	 */
	public static ArrayList<String> getLanguagesForSurvey(Connection connectionSD, int sId) throws Exception {
		
		PreparedStatement pstmtLanguages = null;
		
		ArrayList<String> languages = new ArrayList<String> ();
		try {
			String sqlLanguages = "select distinct t.language from translation t where s_id = ? order by t.language asc";
			pstmtLanguages = connectionSD.prepareStatement(sqlLanguages);
			
			pstmtLanguages.setInt(1, sId);
			ResultSet rs = pstmtLanguages.executeQuery();
			while(rs.next()) {
				languages.add(rs.getString(1));
			}
		} catch(Exception e) {
			log.log(Level.SEVERE,"Error", e);
			throw e;
		} finally {
			try {if (pstmtLanguages != null) {pstmtLanguages.close();}} catch (SQLException e) {}
		}
		return languages;
	}
	
	
}
