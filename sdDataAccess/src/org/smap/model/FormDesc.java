package org.smap.model;

/*
 * Contains form information used in import / export of survey data
 */
import java.util.ArrayList;
import java.util.HashMap;

import org.smap.sdal.model.TableColumn;

public class FormDesc {
	public int f_id;
	public String name;
	public int parent;
	public String table_name;
	public String columns = null;
	public ArrayList<FormDesc> children = null;
	public ArrayList<TableColumn> columnList = null;
	public HashMap<String, String> keyMap = null;
	public FormDesc parentForm = null;
}
