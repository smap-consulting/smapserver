package utilities;

import org.smap.sdal.Utilities.UtilityMethods;

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

public class OptionInfo {
	private String oValue;
	private String targetValue;
	private String oName;
	private String oIdent;
	private String oLabel;
	private String oType;
	
	public OptionInfo() {	
	}
	
	public OptionInfo(String oName, String oValue, String oLabel, String oType) {
		this.oValue = oValue;
		this.oName = UtilityMethods.cleanName(oName);
		this.oLabel = oLabel;
		this.oType = oType;
	}
	
	/*
	 * Getters
	 */
	public String getValue() {
		return oValue;
	}
	
	public String getTargetValue() {
		return targetValue;
	}
	
	public String getName() {
		return oName;
	}
	
	public String getLabel() {
		return oLabel;
	}
	
	public String getIdent() {
		return oIdent;
	}

	/*
	 * Setters
	 */
	public void setOValue(String v) {
		oValue = v;
	}
	
	public void setTargetValue(String v) {
		targetValue = v;
	}
	
	public void setOName(String v) {
		oName = UtilityMethods.cleanName(v);
	}
	
	public void setOLabel(String v) {
		oLabel = v;
	}
	
	public void setIdent(String v) {
		oIdent = v;
	}
	
}
