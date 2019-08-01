package org.smap.model;

import java.util.ArrayList;

import org.smap.sdal.model.KeyValueSimp;
import org.smap.server.entities.Form;

public class Results {
	public String name;
	public Form subForm;	// Non null if this results item is a sub form
	public String value;
	public boolean begin_group;
	public boolean end_group;
	public boolean media;
	public String filename;	// Filename of media
	public ArrayList<KeyValueSimp> parameters;
	public boolean isStartPreload;
	
	public Results (String n, Form f, String v, boolean bg, boolean eg, boolean m, 
			String fn,
			ArrayList<KeyValueSimp> parameters,
			boolean isStartPreload) {
		name = n;
		subForm = f;
		value = v;
		begin_group = bg;
		end_group = eg;
		media = m;
		filename = fn;
		this.parameters = parameters;
		this.isStartPreload = isStartPreload;
	}
}
