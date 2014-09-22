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
import javax.persistence.Table;
import javax.persistence.Transient;

/*
 * Class to store Group objects
 * This is only used by smapMobile (J2ME), fieldTask(Android) stores group information with the question
 */
@Entity(name="GROUP")
@Table(name="QUESTION_GROUP")
public class Group implements Serializable {
	
	private static final long serialVersionUID = 8127117240300471467L;
	
	@Id
	@Column(name="g_id", nullable=false)
	@GeneratedValue(strategy = GenerationType.AUTO, generator="g_seq")
	@SequenceGenerator(name="g_seq", sequenceName="g_seq")
	private int g_id;
	
	@Column(name="qFirst")
	private int firstId = -1;
	
	@Column(name="qLast")
	private int lastId = -1;
	
	@Transient
	private String firstRef = null;
	
	@Transient
	private String lastRef = null;
	
	public Group() {
	}
	
	/*
	 * Getters
	 */
	public int getId() {
		return g_id;
	}
	
	public int getFirstId() {
		return firstId;
	}
	
	public int getLastId() {
		return lastId;
	}
	
	public String getFirstRef() {
		return firstRef;
	}
	public String getLastRef() {
		return lastRef;
	}
	
    /*
     * Setters
     */
    public void setId(int id) {
    	g_id = id;
    }
    
    public void setFirstId(int id) {
    	firstId = id;
    }
    
    public void setLastId(int id) {
    	lastId = id;
    }
    
    public void setFirstRef(String ref) {
    	firstRef = ref;
    }
    
    public void setlastRef(String ref) {
    	lastRef = ref;
    }
    
}
