package org.smap.sdal.model;

/*
 * Form Class
 * Used when loading surveys from a template and the question ID is not known initially
 */
public class RoleColumnFilterRef {
	public int formIndex;		// Used when creating new surveys where the question id is not yet known
	public int questionIndex;
	
	public RoleColumnFilterRef(int formIndex, int questionIndex) {
		this.formIndex = formIndex;
		this.questionIndex = questionIndex;
	}
}
