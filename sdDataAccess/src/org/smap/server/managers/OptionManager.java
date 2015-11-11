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

import org.smap.server.entities.Option;

public class OptionManager {

	private EntityManager em = null;

	public OptionManager(PersistenceContext pc) {
		em = pc.getEntityManager();
	}

	@SuppressWarnings("unchecked")
	public List<Option> getByQuestionId(int questionId) {
		Query query = em.createQuery("SELECT o FROM OPTION o "
				+ "WHERE o.q_id = ?1 " + "ORDER BY o.seq ASC");
		query.setParameter(1, questionId);
		List<Option> optionList = query.getResultList();
		return optionList;
	}

	public int getCountForQuestion(int questionId) {
		Query query = em.createQuery("SELECT COUNT(o) FROM OPTION o "
				+ "WHERE o.q_id = ?1 ");
		query.setParameter(1, questionId);
		Long count = (Long) query.getSingleResult();
		return count.intValue();
	}

	public void persist(Option o) {
		try {
			em.getTransaction().begin();
			em.persist(o);
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			// TODO handle exceptions, specifically constraint violations
		}
	}
}
