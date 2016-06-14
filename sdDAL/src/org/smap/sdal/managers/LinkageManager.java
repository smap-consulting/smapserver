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

package org.smap.sdal.managers;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.model.ChangeElement;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeLog;
import org.smap.sdal.model.ChangeResponse;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Language;
import org.smap.sdal.model.Link;
import org.smap.sdal.model.LinkedSurvey;
import org.smap.sdal.model.ManifestInfo;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.OptionList;
import org.smap.sdal.model.PropertyChange;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.Result;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class LinkageManager {
	
	private static Logger log =
			 Logger.getLogger(LinkageManager.class.getName());

	public ArrayList<Link> getSurveyLinks(Connection sd, Connection cRel, int sId, int fId, int prikey, String hrk) throws SQLException {
		ArrayList<Link> links = new ArrayList<Link> ();
		
		// SQL to get default settings for this user and survey
		ResultSet rs = null;
		String sql = "select f_id from form where parentform = ? and s_id = ?";
		PreparedStatement pstmt = null;
		
		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, fId);
			pstmt.setInt(2, sId);
			log.info("Links:  Getting child forms" + pstmt.toString());
			rs = pstmt.executeQuery();
			while(rs.next()) {
				Link l = new Link();
				l.type = "child";
				l.fId = rs.getInt(1);
				l.parkey = prikey;
				
				links.add(l);
			}
		
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}

		return links;
	}
}
