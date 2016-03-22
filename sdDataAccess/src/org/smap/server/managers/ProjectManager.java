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

package org.smap.server.managers;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.smap.server.entities.MissingTemplateException;
import org.smap.server.entities.Project;
import org.smap.server.entities.Survey;

/**
 * 
 * @author Neil Penman
 * 
 *         This class is used to manipulate Projects from the database.
 */
public class ProjectManager {

	/*
	//private EntityManager em = null;
	private PersistenceContext pc = null;

	public ProjectManager(PersistenceContext pc) {
		// em = pc.getEntityManager();
		this.pc = pc;
	}

	
	 * Retrieves the project by the project's ID.
	 * 
	 * @param id
	 *            The projectID used to retrieve the survey
	 * @return The project found within the database. Returns null if no value
	 *         found.
	 *
	public Project getById(int projectId) {
		EntityManager em = pc.getEntityManager();
		Project p = em.find(Project.class, projectId);
		return p;
	}
	*/

}
