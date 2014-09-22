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
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityNotFoundException;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.Transient;

import org.smap.server.utilities.UtilityMethods;

/*
 * Class to store Form objects
 */
@Entity(name = "FORM")
public class Form implements Serializable {

	private static final long serialVersionUID = -4728483138310578702L;

	// Database Attributes
	@Id
	@Column(name = "f_id", nullable = false)
	@GeneratedValue(strategy = GenerationType.AUTO, generator = "f_seq")
	@SequenceGenerator(name = "f_seq", sequenceName = "f_seq")
	private int f_id;

	@ManyToOne(optional = true, cascade=CascadeType.ALL)
	@JoinColumn(name = "s_id", referencedColumnName = "s_id")
	private Survey surveyOwner = null;

	@Column(name = "name")
	private String name = null;

	@Column(name = "label")
	private String label = null;

	@Column(name = "table_name")
	private String table_name = null;
	
	@ManyToOne(optional = true, cascade=CascadeType.ALL)
	@JoinColumn(name = "parentForm", referencedColumnName = "f_id")
	private Form parentForm = null;

	@ManyToOne(optional = true, cascade=CascadeType.ALL)
	@JoinColumn(name = "parentQuestion", referencedColumnName = "q_id")
	private Question parentQuestion = null;
	
	@Column(name = "repeats")
	private String repeats = null;
	
	@Column(name = "path")
	private String path = null;	// Xpath of this form
	
	/**
	 * This relationship is based on section <a href=
	 * "http://docs.jboss.org/hibernate/stable/annotations/reference/en/html_single/#entity-hibspec-collection-extratype-indexbidir"
	 * > 2.4.6.2.1. Bidirectional association with indexed collections</a> of
	 * the Hibernate documentation.
	 * 
	 * Basically, it ensures that the sequence orders for questions within a
	 * form are managed through the Form object itself.
	 */
	@OneToMany
	/*
	 * This refers to the seq property in the Question object that holds the
	 * list index
	 */
	@OrderColumn(name = "seq")
	/*
	 * This refers to the foreign_key column on the Question table that links it
	 * back to the form
	 */
	@JoinColumn(name = "f_id", nullable = false)
	private List<Question> questions = new ArrayList<Question>();

//	@Transient
//	private String reference; // Unique reference to this form
	
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

	public Survey getSurvey() {
		return surveyOwner;
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

	public String getName() {
		return name;
	}

	public String getLabel() {
		return label;
	}
	
	public String getTableName() {
		return table_name;
	}

	public Form getParentForm() {
		return parentForm;
	}
	
	public List<Question> getQuestions() {
		return questions;
	}

	public Question getParentQuestion() {
		return parentQuestion;
	}
	
	public String getRepeats() {
		return repeats;
	}

	public boolean hasParent() {
		/*
		 * Due to database structure, need to catch exception if no parent is
		 * set.
		 */
		try {
			if (this.getParentForm() == null
					|| this.getParentForm().getId() == 0)
				return false;
			else
				return true;
		} catch (EntityNotFoundException e) {
			return false;
		}
	}
	
	/*
	public String getReference() {
		return reference;
	}
	*/
	
	public String getPath() {
		return path;
	}

	/*
	 * Setters
	 */
	public void setSurvey(Survey survey) {
		surveyOwner = survey;
		// Create the table name from a combination of the survey id and the form name
		table_name = UtilityMethods.cleanName("s" + survey.getId() + "_" + name);
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setParentForm(Form parentForm) {
		this.parentForm = parentForm;
	}

	public void setParentQuestion(Question id) {
		parentQuestion = id;
	}
	
	public void setRepeats(String val) {
		repeats = val;
	}

	public void setQuestions(List<Question> questions) {
		this.questions = questions;
	}

	/*
	public void setReference(String ref) {
		reference = ref;
	}
	*/
	
	public void setPath(String v) {
		path = v;
	}
}
