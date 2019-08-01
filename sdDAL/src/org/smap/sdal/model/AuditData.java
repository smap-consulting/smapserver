package org.smap.sdal.model;

import java.util.HashMap;

public class AuditData {
	public static final String AUDIT_RAW_COLUMN_NAME = "_audit_raw";
	
	public StringBuffer rawAudit = new StringBuffer();
	public HashMap<String, AuditItem> firstPassAudit = new HashMap<>();
}
