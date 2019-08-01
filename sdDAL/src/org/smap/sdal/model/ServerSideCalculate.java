package org.smap.sdal.model;

public class ServerSideCalculate {
	private int id;
	private String name;
	private String fn;
	private String units;
	private String form;
	private int formId;
	
	// Getters
	public int getId() {return id;}; 
	public String getName() {return name;}; 
	public String getFunction() {return fn;};
	public String getUnits() {return units;};
	public String getForm() {return form;};
	public int getFormId() {return formId;};

	
	// Setters
	public void setId(int v) { id = v;};
	public void setName(String v) { name = v;};
	public void setFunction(String v) { fn = v;};
	public void setUnits(String v) { units = v;};
	public void setForm(String v) { form = v;};
	public void setFormId(int v) { formId = v;};
}
