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

import org.smap.server.entities.Question;
import org.smap.server.entities.Survey;

public class QuestionManager {

	private EntityManager em = null;

	public QuestionManager(PersistenceContext pc) {
		em = pc.getEntityManager();
	}

	@SuppressWarnings("unchecked")
	public List<Question> getAll() {
		Query query = em.createQuery("SELECT q FROM QUESTION q "
				+ "ORDER BY q.f_id, q.seq");
		List<Question> questionList = query.getResultList();
		return questionList;
	}

	@SuppressWarnings("unchecked")
	public List<Question> getByFormId(int formId) {
		Query query = em
				.createQuery("SELECT q FROM QUESTION q WHERE q.form.id = ?1 "
						+ "ORDER BY q.seq");
		query.setParameter(1, formId);
		List<Question> questionList = query.getResultList();
		return questionList;
	}

	@SuppressWarnings("unchecked")
	public List<Question> getBySurveyId(int surveyId) {
		Query query = em.createQuery("SELECT q FROM QUESTION q, FORM f WHERE "
				+ "q.form = f.f_id AND f.surveyOwner.id = ?1 "
				+ "ORDER BY q.form, q.seq");
		query.setParameter(1, surveyId);
		List<Question> questionList = query.getResultList();
		return questionList;
	}

	@SuppressWarnings("unchecked")
	public Question getByFormIdQuestionName(int formId, String questionName) {

		Question q = null;
		String qNameLower = questionName.toLowerCase().trim();

		Query query = em
				.createQuery("SELECT q FROM QUESTION q WHERE q.form.id = ?1 AND "
						+ "lower(q.name) = ?2");
		query.setParameter(1, formId);
		query.setParameter(2, qNameLower);
		List<Question> questionList = query.getResultList();

		if (!questionList.isEmpty()) {
			q = questionList.get(0);
		}

		return q;
	}

	@SuppressWarnings("unchecked")
	public List<Question> getChoiceTypeBySurveyId(int surveyId) {

		Query query = em.createQuery("SELECT q FROM QUESTION q, FORM f "
				+ "WHERE q.form.id = f.id AND f.surveyOwner.id = ?1 AND "
				+ "(q.qType = 'select' OR q.qType = 'select1') "
				+ "ORDER BY q.form.id, q.seq");
		query.setParameter(1, surveyId);
		List<Question> questionList = query.getResultList();
		return questionList;
	}

	public List<Question> getBySurvey(Survey survey) {
		Query query = em.createQuery("SELECT q FROM QUESTION q, FORM f WHERE "
				+ "q.form.id = f.id AND f.surveyOwner.id = ?1 "
				+ "ORDER BY q.form.id, q.seq");
		query.setParameter(1, survey.getId());
		List<Question> questionList = query.getResultList();
		return questionList;
	}

	public Question getById(int questionId) {
		return em.find(Question.class, questionId);
	}

	public void remove(Question q) {
		try {
			em.getTransaction().begin();
			em.remove(q);
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			// TODO handle exceptions, specifically constraint violations
		}
	}

	public void persist(Question q) {
		try {
			em.getTransaction().begin();
			em.persist(q);
			em.getTransaction().commit();
		} catch (PersistenceException e) {
			e.printStackTrace();
			em.getTransaction().rollback();
			// TODO handle exceptions, specifically constraint violations
		}
	}
}
