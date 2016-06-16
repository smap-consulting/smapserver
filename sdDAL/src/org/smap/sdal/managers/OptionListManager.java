package org.smap.sdal.managers;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.PropertyChange;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

public class OptionListManager {
	
	private static Logger log =
			 Logger.getLogger(OptionListManager.class.getName());

	public void add(Connection sd, int sId, String name) throws SQLException {
		GeneralUtilityMethods.getListId(sd, sId, name);		// Creates the list if it does not exist
	}
	
	public void delete(Connection sd, int sId, String name) {
		String sql = "delete from listname where s_id = ? and name = ?";
		String sqlClearListIds = "update question set l_id = null, list_name = ? "
				+ "where l_id in (select l_id from listname where s_id = ? and name = ?);";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtClear = null;
		
		try {
			
			pstmtClear = sd.prepareStatement(sqlClearListIds);		// Remember the deleted list name in case a new list of the same name is later added
			pstmtClear.setString(1, name);
			pstmtClear.setInt(2, sId);
			pstmtClear.setString(3, name);
			log.info("Clear list id: " + pstmtClear.toString());
			pstmtClear.executeUpdate();
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			pstmt.setString(2, name);
			
			log.info("Delete list name: " + pstmt.toString());
			pstmt.executeUpdate();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){}
			if(pstmtClear != null) try{pstmtClear.close();}catch(Exception e){}
		}
	}

}
