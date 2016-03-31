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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import JdbcManagers.JdbcQuestionManager;

/*
 * Class to store Form objects
 */
public class Form implements Serializable {

	private static final long serialVersionUID = -4728483138310578702L;

	// Database Attributes
	private int f_id;
	
	private int s_id;
	
	private String name = null;

	private String table_name = null;
	
	private int parentform = 0;
	
	private int parentquestion = 0;
	
	private String repeats = null;
	
	private String path = null;	// Xpath of this form
	
	// Other attributes
	private List<Question> questions = null;
	
	private String parentFormRef = null;
	
	private String repeatsRef = null;
	
	private String parentQuestionRef = null;
	
	public int qSeq = 0;		// Used to store current sequence while saving a forms questions
	/*
	 * Constructor
	 */
	public Form() {
	}

	/*
	 * Getters
	 */
	public int getId() {
		return f_id;
	}
	
	public String getType() {
		/*
		 *  If the form name starts with one of the following reserved words then
		 *  it is a special type, otherwise the type defaults to "form"
		 */
		String type = "form";
		if(name.startsWith("geopolygon")) {
			type = "geopolygon";
		} else if(name.startsWith("geolinestring")) {
			type = "geolinestring";
		}
		
		return type;
	}

	public int getSurveyId() {
		return s_id;
	}
	
	public String getName() {
		return name;
	}
	
	public String getTableName() {
		return table_name;
	}

	public int getParentForm() {
		return parentform;
	}
	
	public List<Question> getQuestions(Connection sd) throws SQLException {
		if(questions == null) {
			//PersistenceContext pc = new PersistenceContext("pgsql_jpa");
			//QuestionManager qm = new QuestionManager(pc);
			//questions = qm.getByFormId(f_id);
			
			JdbcQuestionManager qm = null;
			try {
				qm = new JdbcQuestionManager(sd);
				questions = qm.getByFormId(f_id);
				
			} finally {
				if(qm != null) {qm.close();}
			}
		}
		return questions;
	}

	public int getParentQuestionId() {
		return parentquestion;
	}
	
	public String getParentFormRef() {
		return parentFormRef;
	}
	
	public String getParentQuestionRef() {
		return parentQuestionRef;
	}
	
	public String getRepeatsRef() {
		return repeatsRef;
	}
	
	public String getRepeats() {
		return repeats;
	}

	public boolean hasParent() {
		return parentform != 0;
	}
	
	public String getPath() {
		return path;
	}

	public void setId(int value) {
		f_id = value;
	}
	
	public void setSurveyId(int value) {
		this.s_id = value;
		table_name = "s" + s_id + "_" + name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setTableName(String v) {		// Override automatically generated table name
		table_name = v;
	}

	public void setParentForm(int value) {
		this.parentform = value;
	}

	public void setParentQuestionId(int value) {
		parentquestion = value;
	}
	
	public void setParentFormRef(String value) {
		parentFormRef = value;
	}
	
	public void setParentQuestionRef(String value) {
		parentQuestionRef = value;
	}
	
	public void setRepeatsRef(String val) {
		repeatsRef = val;
	}
	
	public void setRepeats(String val) {
		repeats = val;
	}

	public void setQuestions(List<Question> questions) {
		this.questions = questions;
	}
	
	public void setPath(String v) {
		path = v;
	}
}
