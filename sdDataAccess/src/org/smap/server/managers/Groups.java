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
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.smap.server.entities.Group;

public class Groups {

	private EntityManager em = null;
	
	public Groups(PersistenceContext pc) {
		em = pc.getEntityManager();
	}
	
	
	@SuppressWarnings("unchecked")
	public List <Group> getAll() {
		Query query = em.createQuery("SELECT g FROM GROUP g " +
			"ORDER BY g.g_id");
		List<Group> groupList = query.getResultList();	
		return groupList;
	}
	
	public List <Group> getBySurveyId(int surveyId) {
		Query query = em.createQuery("SELECT g FROM GROUP g, QUESTION q, FORM f WHERE " +
				"q.form.id = f.id AND f.surveyOwner.id = ?1 AND " +
				"g.firstId = q.q_id ");
		query.setParameter(1, surveyId);
		List<Group> groupList = query.getResultList();	
		return groupList;
	}
	
	public void persist(Group g) {
		try {
			em.getTransaction().begin();
			em.persist(g);
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			// TODO handle exceptions, specifically constraint violations
		}
	}
}
