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

import org.smap.server.utilities.UtilityMethods;

/*
 * Class to store Survey objects
 */
@Entity(name = "PROJECT")
public class Project implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2645224176464784459L;

	// Database Attributes
	@Id
	@Column(name="id", nullable=false)
	@GeneratedValue(strategy = GenerationType.AUTO, generator="project_seq")
	@SequenceGenerator(name="project_seq", sequenceName="project_seq")
	private int id;
	
	@Column(name="o_id")
	private int o_id;
	
	@Column(name="name")
	private String name;
	
	@Column(name="changed_by")
	private String changed_by;
	
	/*
	 * Constructor
	 */
	public Project() {
	}
	
	/*
	 * Getters
	 */
	public int getId() {
		return id;
	}
	
	public int getOId() {
		return o_id;
	}
	
	public String getName() {
		return name;
	}
	
	public String getChangedBy() {
		return changed_by;
	}

	
	/*
	 * Setters
	 */
	public void setId(int v) {
		id = v;
	}
	
	public void setOId(int v) {
		o_id = v;
	}
	
	public void setName(String v) {
		name = v;
	}
	
	public void setChangedBy(String v) {
		changed_by = v;
	}
	

}
