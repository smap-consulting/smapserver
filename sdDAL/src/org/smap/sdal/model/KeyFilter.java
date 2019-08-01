package org.smap.sdal.model;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/*
 * Filter created specifically for odata API
 * It is just a column name and a value
 */
public class KeyFilter {
	public String name;
	public String type;		// Set from the table column type string || int
	public String sValue;
	public int iValue;
	
	public void setFilter(PreparedStatement pstmt, int paramCount) throws SQLException {
		if(type != null) {
			if(type.equals("int")) {
				pstmt.setInt(paramCount, iValue);
			} else {		// Default including type String
				pstmt.setString(paramCount, sValue);
			}  
		}
	}
}
