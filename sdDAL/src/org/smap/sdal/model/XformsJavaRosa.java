package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.Date;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name="xforms")
@XmlSeeAlso({ODKForm.class})
public class XformsJavaRosa extends ArrayList<ODKForm> {
	
	//@XmlAttribute
	//public String xmlns = "http://openrosa.org/xforms/xformsList";
	
	public ArrayList<ODKForm> xform;
	
	public XformsJavaRosa() {
		super();		
	}
}
