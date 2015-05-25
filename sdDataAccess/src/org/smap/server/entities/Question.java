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
import java.util.Collection;
import java.util.Vector;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Transient;

import org.smap.server.utilities.UtilityMethods;

/*
 * Class to store Question objects
 * The class handles Create, Read, Update and Delete to/from a database. However
 *  generally Question objects will be created by an external class that populates
 *  question objects from a result set.
 */
@Entity(name = "QUESTION")
public class Question implements Serializable {

	private static final long serialVersionUID = 3323178679751031541L;

	// Database Attributes
	@Id
	@Column(name = "q_id", nullable = false)
	@GeneratedValue(strategy = GenerationType.AUTO, generator = "q_seq")
	@SequenceGenerator(name = "q_seq", sequenceName = "q_seq")
	private int q_id;

	/**
	 * The seq property is used to keep the order of questions within a form. It
	 * can only be updated through the appropriate Form Object's questions list
	 * manipulation.
	 */
	@Column(name = "seq", insertable = false, updatable = false, nullable = false)
	private int seq = -1;

	@Column(name = "qName")
	private String name;
	
	@Column(name = "column_name")
	private String column_name;

	@Column(name = "qType")
	private String qType = "string";

	@Column(name = "question")
	private String question;

	@Column(name = "qtext_id")
	private String qtext_id;
	
	@Column(name = "defaultAnswer")
	private String defaultAnswer;

	@Column(name = "info")
	private String info;
	
	@Column(name = "infotext_id")
	private String infotext_id;

	@Column(name = "visible")
	private boolean visible = false;
	
	@Column(name = "source")
	private String source;
	
	@Column(name = "source_param")
	private String source_param;
	
	@Column(name = "readOnly")
	private boolean readOnly = false;

	@Column(name = "mandatory")
	private boolean mandatory = false;
	
	@Column(name = "relevant")
	private String relevant;

	@Column(name = "calculate")
	private String calculate;
	
	@Column(name = "qconstraint")
	private String constraint;
	
	@Column(name = "constraint_msg")
	private String constraint_msg;
	
	@Column(name = "appearance")
	private String appearance;
	
	//@Column(name = "enabled")
	//private boolean enabled = true;
	
	@Column(name = "path")
	private String path;	// Xpath to this question
	
	@Column(name = "nodeset")
	private String nodeset;	// Nodeset for cascading selects
	
	@Column(name = "nodeset_value")
	private String nodeset_value;
	
	@Column(name = "nodeset_label")
	private String nodeset_label;
	
	@Column(name = "cascade_instance")
	private String cascade_instance;
	
	/**
	 * Used to define the relationship to the form object. This relationship can
	 * only be updated through the appropriate form object.
	 */
	@ManyToOne
	@JoinColumn(name = "f_id", insertable = false, updatable = false, nullable = false)
	private Form form;

	@OneToMany(mappedBy = "question")
	private Collection<Option> choices = null;

	@Transient
	public Vector<String> singleChoiceOptions = null;

	@Transient
	private String formRef; // Unique survey reference to form containing this question

	@Transient
	public String qSubType; // No longer written to database
	
	@Transient
	public String qGroupBeginRef; // Set if this is a dummy question marking the end of a group
	
	/*
	 * Constructor Establish Database Connection using JNDI
	 */
	public Question() {
	}

	/*
	 * Getters
	 */
	public int getId() {
		return q_id;
	}

	public int getSeq() {
		return seq;
	}

	public String getName() {
		return name;
	}
	
	public String getColName() {
		return UtilityMethods.cleanName(name);		// TODO return column_name attribute
	}

	public String getType() {
		return qType;
	}

	public String getSubType() {
		return qSubType;
	}

	public String getQuestion() {
		return question;
	}
	
	public String getQTextId() {
		return qtext_id;
	}

	public String getDefaultAnswer() {
		return defaultAnswer;
	}

	public String getInfo() {
		return info;
	}
	
	public String getInfoTextId() {
		return infotext_id;
	}

	public boolean isVisible() {
		return visible;
	}
	
	public String getSource() {
		return source;
	}
	
	public String getSourceParam() {
		return source_param;
	}
	
	public boolean isReadOnly() {
		return readOnly;
	}

	public boolean isMandatory() {
		return mandatory;
	}

	public String getRelevant() {
		return relevant;
	}
	
	public String getCalculate() {
		return calculate;
	}
	
	public String getConstraint() {
		return constraint;
	}
	
	public String getConstraintMsg() {
		return constraint_msg;
	}
	
	public String getAppearance() {
		return appearance;
	}
	
	public boolean getEnabled() {		// deprecate
		return true;
	}
	
	public String getPath() {

		String tPath = null;
		tPath = path;
		
		return tPath;
	}
	
	public String getNodeset() {
		return nodeset;
	}
	
	public String getNodesetValue() {
		return nodeset_value;
	}
	
	public String getNodesetLabel() {
		return nodeset_label;
	}
	
	public String getCascadeInstance() {
		return cascade_instance;
	}
	
	/*
	 * Setters
	 */

	public void setSeq(int seq) {
		this.seq = seq;
	}

	public void setName(String name) {
		this.name = name;
		this.column_name = UtilityMethods.cleanName(name);
	}

	public void setType(String type) {
		qType = type;
	}

	public void setQuestion(String question) {
		this.question = question;
	}
	
	public void setQTextId(String value) {
		qtext_id = value;
	}

	public void setDefaultAnswer(String defaultAnswer) {
		this.defaultAnswer = defaultAnswer;
	}

	public void setInfo(String info) {
		this.info = info;
	}
	
	public void setInfoTextId(String value) {
		infotext_id = value;
	}

	public void setVisible(boolean v) {
		visible = v;
	}
	
	public void setSource(String v) {
		source = v;
	}
	
	public void setSourceParam(String v) {
		source_param = v;
	}
	
	public void setReadOnly(boolean ro) {
		readOnly = ro;
	}

	public void setReadOnly(String ro) {
		readOnly = ro.toLowerCase().startsWith("t");
	}

	public void setMandatory(boolean man) {
		mandatory = man;
	}
	
	public void setRelevant(String rel) {
		relevant = rel;
	}
	
	public void setConstraint(String v) {
		constraint = v;
	}
	
	public void setCalculate(String v) {
		calculate = v;
	}
	
	public void setConstraintMsg(String v) {
		constraint_msg = v;
	}
	
	public void setAppearance(String v) {
		appearance = v;
	}
	
	/*
	public void setEnabled(boolean v) {
		enabled = v;
	}
	*/

	public void setFormRef(String formRef) {
		this.formRef = formRef;
	}
	
	public void setBeginRef(String v) {
		this.qGroupBeginRef = v;
	}
	
	public String getFormRef() {
		return formRef;
	}
	
	public String getBeginRef() {
		return qGroupBeginRef;
	}
	
	public void setPath(String v) {
		path = v;
	}

	public void setNodeset(String v) {
		nodeset = v;
	}
	
	public void setNodesetValue(String v) {
		nodeset_value = v;
	}
	
	public void setNodesetLabel(String v) {
		nodeset_label = v;
	}
	
	public void setCascadeInstance(String v) {
		cascade_instance = v;
	}
	
	/*
	 * Set the type value in the question object based on the type and subtype
	 * values received from the OSM XML file
	 */
	public void cleanseOSM() {

		if (qType != null) {
			if (qType.equals("choice")) {
				if (qSubType != null && qSubType.equals("radio")) {
					qType = "select1";
				} else {
					qType = "select";
				}
			} else if (qType.equals("singleChoice")) {
				qType = "select1";
			} else if (qType.equals("text")) {
				if (qSubType == null) {
					qType = "string";
				} else if (qSubType.equals("A")) {
					qType = "string";
				} else if (qSubType.equals("N")) {
					qType = "int";
				} else if (qSubType.equals("D")) {
					qType = "decimal";
				}
			} else if (qType.equals("date")) {
				if (qSubType == "DT") {
					qType = "dateTime";
				} else if (qSubType.equals("D")) {
					qType = "date";
				} else if (qSubType.equals("T")) {
					qType = "time";
				}
			} else {
				System.out.println("Unknown type in cleanseOSM: " + qType);
			}
		}
	}

	public void setForm(Form form) {
		this.form = form;
	}

	public Form getForm() {
		return form;
	}

	public void setChoices(Collection<Option> choices) {
		this.choices = choices;
	}

	/*
	 * Only return choices that were not created by an external csv file
	 */
	public Collection<Option> getChoices() {
		Collection<Option> internalChoices = new ArrayList<Option> ();
		
		ArrayList<Option> cArray = new ArrayList<Option>(choices);
		for(int i = 0; i < cArray.size(); i++) {
			if(!cArray.get(i).getExternalFile()) {
				internalChoices.add(cArray.get(i));
			}
		}
		return internalChoices;
	}
	
	/*
	 * Return all non external choices 
	 *   or if there is a single external choice then return all external choices
	 */
	public Collection<Option> getValidChoices() {
		
		Collection<Option> externalChoices = new ArrayList<Option> ();
		ArrayList<Option> cArray = new ArrayList<Option>(choices);
		boolean external = false;
		for(int i = 0; i < cArray.size(); i++) {
			if(cArray.get(i).getExternalFile()) {
				external = true;
				externalChoices.add(cArray.get(i));
			}
		}
		if(external) {
			return externalChoices;
		} else {
			return choices;
		}
	}

	public String toString() {
		StringBuffer returnBuffer = new StringBuffer();
		returnBuffer.append("q_id=" + this.getId());
		returnBuffer.append(",");
		if (form != null) {
			returnBuffer.append("f_id=" + this.getForm().getId());
			returnBuffer.append(",");
		}
		returnBuffer.append("seq=" + this.getSeq());
		returnBuffer.append(",");
		returnBuffer.append("qname=" + this.getName());
		returnBuffer.append(",");
		returnBuffer.append("qtype=" + this.getType());
		returnBuffer.append(",");
		returnBuffer.append("questionId=" + this.getQTextId());
		returnBuffer.append(",");
		returnBuffer.append("defaultanswer=" + this.getDefaultAnswer());
		returnBuffer.append(",");
		returnBuffer.append("infoId=" + this.getInfoTextId());
		returnBuffer.append(",");
		returnBuffer.append("readonly=" + this.readOnly);
		return returnBuffer.toString();
	}
}