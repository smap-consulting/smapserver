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

package org.smap.server.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.server.utilities.UtilityMethods;

/*
 * Class to store Survey objects
 */
@Entity(name = "SURVEY")
public class Survey implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2645224176464784459L;

	// Database Attributes
	@Id
	@Column(name="s_id", nullable=false)
	@GeneratedValue(strategy = GenerationType.AUTO, generator="s_seq")
	@SequenceGenerator(name="s_seq", sequenceName="s_seq")
	private int id;
	
	@Column(name="name")
	private String name;
	
	@Column(name="ident")
	private String ident;
	
	@Column(name="display_name")
	private String display_name;
	
	@Column(name="def_lang")
	private String def_lang;
	
	@Column(name="p_id")
	private int p_id;
	
	@Column(name="deleted")
	private boolean deleted = false;
	
	@Column(name="version")
	private int version;
	
	@Column(name="manifest")
	private String manifest;
	
	@Column(name="class")
	private String surveyClass;
	
	/*
	 * Constructor
	 */
	public Survey() {
	}
	
	/*
	 * Getters
	 */
	public int getId() {
		return id;
	}
	
	public String getIdent() {
		return ident;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDisplayName() {
		return display_name;
	}
	
	public String getDefLang() {
		return def_lang;
	}
	
	public String getSurveyClass() {
		return surveyClass;
	}
	
	public int getProjectId() {
		return p_id;
	}
	
	public boolean getDeleted() {
		return deleted;
	}
	
	public int getVersion() {
		return version;
	}
	
	public String getManifest() {
		return manifest;
	}
	
	// Get the display name with any HTML reserved characters escaped
	public String getDisplayNameForHTML() {
		return GeneralUtilityMethods.esc(display_name);
	}
	
	/*
	 * Setters
	 */
	
	public void setDisplayName(String v) {
		display_name = v;
	}
	
	public void setIdent(String v) {
		ident = v;
	}
	
	public void setFileName(String v) {
		name = v;
	}
	
	public void setDefLang(String v) {
		def_lang = v;
	}
	
	public void setSurveyClass(String v) {
		surveyClass = v;
	}
	
	public void setProjectId(int v) {
		p_id = v;
	}
	
	public void setVersion(int v) {
		version = v;
	}
	
	public void setManifest(String v) {
		manifest = v;
	}
	

}
