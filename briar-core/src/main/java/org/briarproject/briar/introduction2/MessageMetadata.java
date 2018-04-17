package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.client.SessionId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class MessageMetadata {

	private final MessageType type;
	@Nullable
	private final SessionId sessionId;
	private final long timestamp;
	private final boolean local, read, visible, available, accepted;

	MessageMetadata(MessageType type, @Nullable SessionId sessionId,
			long timestamp, boolean local, boolean read, boolean visible,
			boolean available, boolean accepted) {
		this.type = type;
		this.sessionId = sessionId;
		this.timestamp = timestamp;
		this.local = local;
		this.read = read;
		this.visible = visible;
		this.available = available;
		this.accepted = accepted;
	}

	MessageType getMessageType() {
		return type;
	}

	@Nullable
	public SessionId getSessionId() {
		return sessionId;
	}

	long getTimestamp() {
		return timestamp;
	}

	boolean isLocal() {
		return local;
	}

	boolean isRead() {
		return read;
	}

	boolean isVisibleInConversation() {
		return visible;
	}

	boolean isAvailableToAnswer() {
		return available;
	}

	public boolean wasAccepted() {
		return accepted;
	}

}
