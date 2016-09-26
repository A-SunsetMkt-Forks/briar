package org.briarproject.introduction;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.introduction.IntroduceeProtocolState;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.ADDED_CONTACT_ID;
import static org.briarproject.api.introduction.IntroductionConstants.ANSWERED;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.LOCAL_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.api.introduction.IntroductionConstants.MAC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.MAC_LENGTH;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NONCE;
import static org.briarproject.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_IS_US;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE;
import static org.briarproject.api.introduction.IntroductionConstants.ROLE_INTRODUCEE;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.STORAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.hamcrest.Matchers.array;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IntroduceeManagerTest extends BriarTestCase {

	private final Mockery context;
	private final IntroduceeManager introduceeManager;
	private final DatabaseComponent db;
	private final CryptoComponent cryptoComponent;
	private final ClientHelper clientHelper;
	private final IntroductionGroupFactory introductionGroupFactory;
	private final MessageSender messageSender;
	private final TransportPropertyManager transportPropertyManager;
	private final AuthorFactory authorFactory;
	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final Clock clock;
	private final Contact introducer;
	private final Contact introducee1;
	private final Contact introducee2;
	private final Group localGroup1;
	private final Group introductionGroup1;
	private final Transaction txn;
	private final long time = 42L;
	private final Message localStateMessage;
	private final ClientId clientId;
	private final SessionId sessionId;
	private final Message message1;

	public IntroduceeManagerTest() {
		context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		messageSender = context.mock(MessageSender.class);
		db = context.mock(DatabaseComponent.class);
		cryptoComponent = context.mock(CryptoComponent.class);
		clientHelper = context.mock(ClientHelper.class);
		clock = context.mock(Clock.class);
		introductionGroupFactory =
				context.mock(IntroductionGroupFactory.class);
		transportPropertyManager = context.mock(TransportPropertyManager.class);
		authorFactory = context.mock(AuthorFactory.class);
		contactManager = context.mock(ContactManager.class);
		identityManager = context.mock(IdentityManager.class);

		introduceeManager = new IntroduceeManager(messageSender, db,
				clientHelper, clock, cryptoComponent, transportPropertyManager,
				authorFactory, contactManager, identityManager,
				introductionGroupFactory);

		AuthorId authorId0 = new AuthorId(TestUtils.getRandomId());
		Author author0 = new Author(authorId0, "Introducer",
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		AuthorId localAuthorId = new AuthorId(TestUtils.getRandomId());
		ContactId contactId0 = new ContactId(234);
		introducer =
				new Contact(contactId0, author0, localAuthorId, true, true);

		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		Author author1 = new Author(authorId1, "Introducee1",
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		AuthorId localAuthorId1 = new AuthorId(TestUtils.getRandomId());
		ContactId contactId1 = new ContactId(234);
		introducee1 =
				new Contact(contactId1, author1, localAuthorId1, true, true);

		AuthorId authorId2 = new AuthorId(TestUtils.getRandomId());
		Author author2 = new Author(authorId2, "Introducee2",
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		ContactId contactId2 = new ContactId(235);
		introducee2 =
				new Contact(contactId2, author2, localAuthorId, true, true);

		clientId = IntroductionManagerImpl.CLIENT_ID;
		localGroup1 = new Group(new GroupId(TestUtils.getRandomId()),
				clientId, new byte[0]);
		introductionGroup1 = new Group(new GroupId(TestUtils.getRandomId()),
				clientId, new byte[0]);

		sessionId = new SessionId(TestUtils.getRandomId());
		localStateMessage = new Message(
				new MessageId(TestUtils.getRandomId()),
				localGroup1.getId(),
				time,
				TestUtils.getRandomBytes(MESSAGE_HEADER_LENGTH + 1)
		);
		message1 = new Message(
				new MessageId(TestUtils.getRandomId()),
				introductionGroup1.getId(),
				time,
				TestUtils.getRandomBytes(MESSAGE_HEADER_LENGTH + 1)
		);

		txn = new Transaction(null, false);
	}

	@Test
	public void testIncomingRequestMessage()
			throws DbException, FormatException {

		final BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_REQUEST);
		msg.put(GROUP_ID, introductionGroup1.getId());
		msg.put(SESSION_ID, sessionId);
		msg.put(MESSAGE_ID, message1.getId());
		msg.put(MESSAGE_TIME, time);
		msg.put(NAME, introducee2.getAuthor().getName());
		msg.put(PUBLIC_KEY, introducee2.getAuthor().getPublicKey());

		final BdfDictionary state =
				initializeSessionState(txn, introductionGroup1.getId(), msg);

		context.checking(new Expectations() {{
			oneOf(clientHelper).mergeMessageMetadata(txn,
					localStateMessage.getId(), state);
		}});

		introduceeManager.incomingMessage(txn, state, msg);

		context.assertIsSatisfied();

		assertFalse(txn.isComplete());
	}

	@Test
	public void testIncomingResponseMessage()
			throws DbException, FormatException {

		final BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_RESPONSE);
		msg.put(GROUP_ID, introductionGroup1.getId());
		msg.put(SESSION_ID, sessionId);
		msg.put(MESSAGE_ID, message1.getId());
		msg.put(MESSAGE_TIME, time);
		msg.put(NAME, introducee2.getAuthor().getName());
		msg.put(PUBLIC_KEY, introducee2.getAuthor().getPublicKey());

		final BdfDictionary state =
				initializeSessionState(txn, introductionGroup1.getId(), msg);
		state.put(STATE, IntroduceeProtocolState.AWAIT_RESPONSES.ordinal());

		// turn request message into a response
		msg.put(ACCEPT, true);
		msg.put(TIME, time);
		msg.put(E_PUBLIC_KEY, TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
		msg.put(TRANSPORT, new BdfDictionary());

		context.checking(new Expectations() {{
			oneOf(clientHelper).mergeMessageMetadata(txn,
					localStateMessage.getId(), state);
		}});

		introduceeManager.incomingMessage(txn, state, msg);

		context.assertIsSatisfied();

		assertFalse(txn.isComplete());
	}

	@Test
	public void testDetectReplacedEphemeralPublicKey()
			throws DbException, FormatException, GeneralSecurityException {

		// TODO MR !237 should use its new default initialization method here
		final BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_RESPONSE);
		msg.put(GROUP_ID, introductionGroup1.getId());
		msg.put(SESSION_ID, sessionId);
		msg.put(MESSAGE_ID, message1.getId());
		msg.put(MESSAGE_TIME, time);
		msg.put(NAME, introducee2.getAuthor().getName());
		msg.put(PUBLIC_KEY, introducee2.getAuthor().getPublicKey());
		final BdfDictionary state =
				initializeSessionState(txn, introductionGroup1.getId(), msg);

		// prepare state for incoming ACK
		state.put(STATE, IntroduceeProtocolState.AWAIT_ACK.ordinal());
		state.put(ADDED_CONTACT_ID, 2);
		final byte[] nonce = TestUtils.getRandomBytes(42);
		state.put(NONCE, nonce);
		state.put(PUBLIC_KEY, introducee2.getAuthor().getPublicKey());

		// create incoming ACK message
		final byte[] mac = TestUtils.getRandomBytes(MAC_LENGTH);
		final byte[] sig = TestUtils.getRandomBytes(MAX_SIGNATURE_LENGTH);
		BdfDictionary ack = BdfDictionary.of(
				new BdfEntry(TYPE, TYPE_ACK),
				new BdfEntry(SESSION_ID, sessionId),
				new BdfEntry(GROUP_ID, introductionGroup1.getId()),
				new BdfEntry(MAC, mac),
				new BdfEntry(SIGNATURE, sig)
		);

		final KeyParser keyParser = context.mock(KeyParser.class);
		final PublicKey publicKey = context.mock(PublicKey.class);
		final Signature signature = context.mock(Signature.class);
		context.checking(new Expectations() {{
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser)
					.parsePublicKey(introducee2.getAuthor().getPublicKey());
			will(returnValue(publicKey));
			oneOf(cryptoComponent).getSignature();
			will(returnValue(signature));
			oneOf(signature).initVerify(publicKey);
			oneOf(signature).update(nonce);
			oneOf(signature).verify(sig);
			will(returnValue(false));
		}});

		try {
			introduceeManager.incomingMessage(txn, state, ack);
			fail();
		} catch (DbException e) {
			// expected
			assertTrue(e.getCause() instanceof GeneralSecurityException);
		}
		context.assertIsSatisfied();
		assertFalse(txn.isComplete());
	}

	@Test
	public void testSignatureVerification()
			throws FormatException, DbException, GeneralSecurityException {

		final byte[] publicKeyBytes = introducee2.getAuthor().getPublicKey();
		final byte[] nonce = TestUtils.getRandomBytes(MAC_LENGTH);
		final byte[] sig = TestUtils.getRandomBytes(MAC_LENGTH);

		BdfDictionary state = new BdfDictionary();
		state.put(PUBLIC_KEY, publicKeyBytes);
		state.put(NONCE, nonce);
		state.put(SIGNATURE, sig);

		final KeyParser keyParser = context.mock(KeyParser.class);
		final Signature signature = context.mock(Signature.class);
		final PublicKey publicKey = context.mock(PublicKey.class);
		context.checking(new Expectations() {{
			oneOf(cryptoComponent).getSignatureKeyParser();
			will(returnValue(keyParser));
			oneOf(keyParser).parsePublicKey(publicKeyBytes);
			will(returnValue(publicKey));
			oneOf(cryptoComponent).getSignature();
			will(returnValue(signature));
			oneOf(signature).initVerify(publicKey);
			oneOf(signature).update(nonce);
			oneOf(signature).verify(sig);
			will(returnValue(true));
		}});
		introduceeManager.verifySignature(state);
		context.assertIsSatisfied();
	}

	@Test
	public void testMacVerification()
			throws FormatException, DbException, GeneralSecurityException {

		final byte[] publicKeyBytes = introducee2.getAuthor().getPublicKey();
		final BdfDictionary tp = BdfDictionary.of(new BdfEntry("fake", "fake"));
		final byte[] ePublicKeyBytes =
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		final byte[] mac = TestUtils.getRandomBytes(MAC_LENGTH);
		final SecretKey macKey = TestUtils.getSecretKey();

		// move state to where it would be after an ACK arrived
		BdfDictionary state = new BdfDictionary();
		state.put(PUBLIC_KEY, publicKeyBytes);
		state.put(TRANSPORT, tp);
		state.put(TIME, time);
		state.put(E_PUBLIC_KEY, ePublicKeyBytes);
		state.put(MAC, mac);
		state.put(MAC_KEY, macKey.getBytes());

		final byte[] signBytes = TestUtils.getRandomBytes(42);
		context.checking(new Expectations() {{
			oneOf(clientHelper).toByteArray(
					BdfList.of(publicKeyBytes, ePublicKeyBytes, tp, time));
			will(returnValue(signBytes));
			//noinspection unchecked
			oneOf(cryptoComponent).mac(with(samePropertyValuesAs(macKey)),
					with(array(equal(signBytes))));
			will(returnValue(mac));
		}});
		introduceeManager.verifyMac(state);
		context.assertIsSatisfied();

		// now produce wrong MAC
		context.checking(new Expectations() {{
			oneOf(clientHelper).toByteArray(
					BdfList.of(publicKeyBytes, ePublicKeyBytes, tp, time));
			will(returnValue(signBytes));
			//noinspection unchecked
			oneOf(cryptoComponent).mac(with(samePropertyValuesAs(macKey)),
					with(array(equal(signBytes))));
			will(returnValue(TestUtils.getRandomBytes(MAC_LENGTH)));
		}});
		try {
			introduceeManager.verifyMac(state);
			fail();
		} catch(GeneralSecurityException e) {
			// expected
		}
		context.assertIsSatisfied();
	}

	private BdfDictionary initializeSessionState(final Transaction txn,
			final GroupId groupId, final BdfDictionary msg)
			throws DbException, FormatException {

		final SecureRandom secureRandom = context.mock(SecureRandom.class);
		final Bytes salt = new Bytes(new byte[64]);
		final BdfDictionary groupMetadata = BdfDictionary.of(
				new BdfEntry(CONTACT, introducee1.getId().getInt())
		);
		final boolean contactExists = false;
		final BdfDictionary state = new BdfDictionary();
		state.put(STORAGE_ID, localStateMessage.getId());
		state.put(STATE, AWAIT_REQUEST.getValue());
		state.put(ROLE, ROLE_INTRODUCEE);
		state.put(GROUP_ID, groupId);
		state.put(INTRODUCER, introducer.getAuthor().getName());
		state.put(CONTACT_ID_1, introducer.getId().getInt());
		state.put(LOCAL_AUTHOR_ID, introducer.getLocalAuthorId().getBytes());
		state.put(NOT_OUR_RESPONSE, localStateMessage.getId());
		state.put(ANSWERED, false);
		state.put(EXISTS, contactExists);
		state.put(REMOTE_AUTHOR_ID, introducee2.getAuthor().getId());
		state.put(REMOTE_AUTHOR_IS_US, false);

		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(time));
			oneOf(cryptoComponent).getSecureRandom();
			will(returnValue(secureRandom));
			oneOf(secureRandom).nextBytes(salt.getBytes());
			oneOf(introductionGroupFactory).createLocalGroup();
			will(returnValue(localGroup1));
			oneOf(clientHelper)
					.createMessage(localGroup1.getId(), time, BdfList.of(salt));
			will(returnValue(localStateMessage));

			// who is making the introduction? who is the introducer?
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					groupId);
			will(returnValue(groupMetadata));
			oneOf(db).getContact(txn, introducer.getId());
			will(returnValue(introducer));

			// create remote author to check if contact exists
			oneOf(authorFactory).createAuthor(introducee2.getAuthor().getName(),
					introducee2.getAuthor().getPublicKey());
			will(returnValue(introducee2.getAuthor()));
			oneOf(contactManager)
					.contactExists(txn, introducee2.getAuthor().getId(),
							introducer.getLocalAuthorId());
			will(returnValue(contactExists));

			// checks if remote author is one of our identities
			oneOf(db).containsLocalAuthor(txn, introducee2.getAuthor().getId());
			will(returnValue(false));

			// store session state
			oneOf(clientHelper)
					.addLocalMessage(txn, localStateMessage, state, false);
		}});

		BdfDictionary result = introduceeManager.initialize(txn, groupId, msg);

		context.assertIsSatisfied();
		return result;
	}

}
