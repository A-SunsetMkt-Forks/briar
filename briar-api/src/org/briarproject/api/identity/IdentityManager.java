package org.briarproject.api.identity;

import org.briarproject.api.db.DbException;

import java.util.Collection;

public interface IdentityManager {

	/** Stores a local pseudonym. */
	void addLocalAuthor(LocalAuthor a) throws DbException;

	/** Returns the local pseudonym with the given ID. */
	LocalAuthor getLocalAuthor(AuthorId a) throws DbException;

	/** Returns all local pseudonyms. */
	Collection<LocalAuthor> getLocalAuthors() throws DbException;

	/** Removes a local pseudonym and all associated state. */
	void removeLocalAuthor(AuthorId a) throws DbException;
}
