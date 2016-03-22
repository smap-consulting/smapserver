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

package JdbcManagers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import javax.persistence.PersistenceException;

import org.smap.server.entities.Survey;
import org.smap.server.entities.Translation;

public class JdbcTranslationManager {

	PreparedStatement pstmt = null;
	String sql = "insert into translation (s_id, language, text_id, type, value) values (?, ?, ?, ?, ?);";
	
	public JdbcTranslationManager(Connection sd) throws SQLException {
		pstmt = sd.prepareStatement(sql);
	}
	
	public void write(int sId, String language, String text_id, String type, String value) throws SQLException {
		pstmt.setInt(1, sId);
		pstmt.setString(2, language);
		pstmt.setString(3, text_id);
		pstmt.setString(4, type);
		pstmt.setString(5, value);
		pstmt.executeUpdate();
	}
	
	public void persistBatch(int sId, Collection<HashMap<String, Translation>> l) throws SQLException {
		
		Iterator<HashMap<String,Translation>> itrL = l.iterator();
		while(itrL.hasNext()) {								
			HashMap<String,Translation> types = (HashMap<String, Translation>) itrL.next();
			
			Collection<Translation> t = types.values();
			Iterator<Translation> itrT = t.iterator();

			while(itrT.hasNext()) {
				Translation trans = (Translation) itrT.next();
				System.out.println("Write: " + trans.getValue());
				write(sId, trans.getLanguage(), trans.getTextId(), trans.getType(), trans.getValue());
			}
		}
			
	}
	
	public void close() {
		try {if(pstmt != null) {pstmt.close();}} catch(Exception e) {};
	}
}
