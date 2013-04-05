package net.sf.briar.api.transport;

import static net.sf.briar.api.transport.TransportConstants.CONNECTION_WINDOW_SIZE;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;

public class TemporarySecret extends Endpoint {

	private final long period, outgoing, centre;
	private final byte[] secret, bitmap;

	/** Creates a temporary secret with the given connection window. */
	public TemporarySecret(ContactId contactId, TransportId transportId,
			long epoch, boolean alice, long period, byte[] secret,
			long outgoing, long centre, byte[] bitmap) {
		super(contactId, transportId, epoch, alice);
		this.period = period;
		this.secret = secret;
		this.outgoing = outgoing;
		this.centre = centre;
		this.bitmap = bitmap;
	}

	/** Creates a temporary secret with a new connection window. */
	public TemporarySecret(ContactId contactId, TransportId transportId,
			long epoch, boolean alice, long period, byte[] secret) {
		this(contactId, transportId, epoch, alice, period, secret, 0, 0,
				new byte[CONNECTION_WINDOW_SIZE / 8]);
	}

	/** Creates a temporary secret derived from the given endpoint. */
	public TemporarySecret(Endpoint ep, long period, byte[] secret) {
		this(ep.getContactId(), ep.getTransportId(), ep.getEpoch(),
				ep.getAlice(), period, secret);
	}

	public long getPeriod() {
		return period;
	}

	public byte[] getSecret() {
		return secret;
	}

	public long getOutgoingConnectionCounter() {
		return outgoing;
	}

	public long getWindowCentre() {
		return centre;
	}

	public byte[] getWindowBitmap() {
		return bitmap;
	}

	@Override
	public int hashCode() {
		int periodHashCode = (int) (period ^ (period >>> 32));
		return contactId.hashCode() ^ transportId.hashCode() ^ periodHashCode;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof TemporarySecret) {
			TemporarySecret s = (TemporarySecret) o;
			return contactId.equals(s.contactId) &&
					transportId.equals(s.transportId) && period == s.period;
		}
		return false;
	}
}
