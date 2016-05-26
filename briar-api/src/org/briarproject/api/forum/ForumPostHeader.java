package org.briarproject.api.forum;

import org.briarproject.api.clients.MessageTree;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.MessageId;

public class ForumPostHeader implements MessageTree.MessageNode {

	private final MessageId id;
	private final MessageId parentId;
	private final long timestamp;
	private final Author author;
	private final Author.Status authorStatus;
	private final String contentType;
	private final boolean read;

	public ForumPostHeader(MessageId id, MessageId parentId, long timestamp,
			Author author, Author.Status authorStatus, String contentType,
			boolean read) {
		this.id = id;
		this.parentId = parentId;
		this.timestamp = timestamp;
		this.author = author;
		this.authorStatus = authorStatus;
		this.contentType = contentType;
		this.read = read;
	}

	public MessageId getId() {
		return id;
	}

	public Author getAuthor() {
		return author;
	}

	public Author.Status getAuthorStatus() {
		return authorStatus;
	}

	public String getContentType() {
		return contentType;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public boolean isRead() {
		return read;
	}

	public MessageId getParentId() {
		return parentId;
	}
}
