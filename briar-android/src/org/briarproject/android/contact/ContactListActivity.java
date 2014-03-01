package org.briarproject.android.contact;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.AuthorId;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageExpiredEvent;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.transport.ConnectionListener;
import org.briarproject.api.transport.ConnectionRegistry;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class ContactListActivity extends BriarActivity
implements OnClickListener, OnItemClickListener, EventListener,
ConnectionListener {

	private static final Logger LOG =
			Logger.getLogger(ContactListActivity.class.getName());

	@Inject private ConnectionRegistry connectionRegistry;
	private TextView empty = null;
	private ContactListAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private ImageButton addContactButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		empty = new TextView(this);
		empty.setLayoutParams(MATCH_WRAP_1);
		empty.setGravity(CENTER);
		empty.setTextSize(18);
		empty.setText(R.string.no_contacts);
		empty.setVisibility(GONE);
		layout.addView(empty);

		adapter = new ContactListAdapter(this);
		list = new ListView(this);
		list.setLayoutParams(MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		list.setVisibility(GONE);
		layout.addView(list);

		// Show a progress bar while the list is loading
		loading = new ListLoadingProgressBar(this);
		layout.addView(loading);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setGravity(CENTER);
		Resources res = getResources();
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));
		addContactButton = new ImageButton(this);
		addContactButton.setBackgroundResource(0);
		addContactButton.setImageResource(R.drawable.social_add_person);
		addContactButton.setOnClickListener(this);
		footer.addView(addContactButton);
		layout.addView(footer);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		connectionRegistry.addListener(this);
		loadContacts();
	}

	private void loadContacts() {
		clearContacts();
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Map<ContactId, Long> times = db.getLastConnected();
					for(Contact c : db.getContacts()) {
						Long lastConnected = times.get(c.getId());
						if(lastConnected == null) continue;
						try {
							GroupId inbox = db.getInboxGroupId(c.getId());
							Collection<MessageHeader> headers =
									db.getInboxMessageHeaders(c.getId());
							displayContact(c, lastConnected, inbox, headers);
						} catch(NoSuchContactException e) {
							// Continue
						}
					}
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Full load took " + duration + " ms");
					hideProgressBar();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void clearContacts() {
		runOnUiThread(new Runnable() {
			public void run() {
				empty.setVisibility(GONE);
				list.setVisibility(GONE);
				loading.setVisibility(VISIBLE);
				adapter.clear();
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void displayContact(final Contact c, final long lastConnected,
			final GroupId inbox, final Collection<MessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				boolean connected = connectionRegistry.isConnected(c.getId());
				// Remove the old item, if any
				ContactListItem item = findItem(c.getId());
				if(item != null) adapter.remove(item);
				// Add a new item
				adapter.add(new ContactListItem(c, connected, lastConnected,
						inbox, headers));
				adapter.sort(ContactListItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void hideProgressBar() {
		runOnUiThread(new Runnable() {
			public void run() {
				if(adapter.isEmpty()) empty.setVisibility(VISIBLE);
				else list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
			}
		});
	}

	private ContactListItem findItem(ContactId c) {
		int count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			ContactListItem item = adapter.getItem(i);
			if(item.getContact().getId().equals(c)) return item;
		}
		return null; // Not found
	}

	@Override
	public void onPause() {
		super.onPause();
		db.removeListener(this);
		connectionRegistry.removeListener(this);
	}

	public void onClick(View view) {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		ContactListItem item = adapter.getItem(position);
		ContactId contactId = item.getContact().getId();
		String contactName = item.getContact().getAuthor().getName();
		GroupId inbox = item.getInboxGroupId();
		AuthorId localAuthorId = item.getContact().getLocalAuthorId();
		Intent i = new Intent(this, ConversationActivity.class);
		i.putExtra("briar.CONTACT_ID", contactId.getInt());
		i.putExtra("briar.CONTACT_NAME", contactName);
		i.putExtra("briar.GROUP_ID", inbox.getBytes());
		i.putExtra("briar.LOCAL_AUTHOR_ID", localAuthorId.getBytes());
		startActivity(i);
	}

	public void eventOccurred(Event e) {
		if(e instanceof ContactAddedEvent) {
			loadContacts();
		} else if(e instanceof ContactRemovedEvent) {
			// Reload the conversation, expecting NoSuchContactException
			if(LOG.isLoggable(INFO)) LOG.info("Contact removed, reloading");
			reloadContact(((ContactRemovedEvent) e).getContactId());
		} else if(e instanceof MessageAddedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
			ContactId source = ((MessageAddedEvent) e).getContactId();
			if(source == null) loadContacts();
			else reloadContact(source);
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadContacts();
		}
	}

	private void reloadContact(final ContactId c) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<MessageHeader> headers =
							db.getInboxMessageHeaders(c);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Partial load took " + duration + " ms");
					updateItem(c, headers);
				} catch(NoSuchContactException e) {
					removeItem(c);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void updateItem(final ContactId c,
			final Collection<MessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = findItem(c);
				if(item != null) {
					item.setHeaders(headers);
					adapter.notifyDataSetChanged();
				}
			}
		});
	}

	private void removeItem(final ContactId c) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = findItem(c);
				if(item != null) {
					adapter.remove(item);
					adapter.notifyDataSetChanged();
					if(adapter.isEmpty()) {
						empty.setVisibility(VISIBLE);
						list.setVisibility(GONE);
					}
				}
			}
		});
	}

	public void contactConnected(ContactId c) {
		setConnected(c, true);
	}

	public void contactDisconnected(ContactId c) {
		setConnected(c, false);
	}

	private void setConnected(final ContactId c, final boolean connected) {
		runOnUiThread(new Runnable() {
			public void run() {
				ContactListItem item = findItem(c);
				if(item == null) return;
				if(LOG.isLoggable(INFO))
					LOG.info("Setting connection status " + connected);
				item.setConnected(connected);
				item.setLastConnected(System.currentTimeMillis());
				adapter.notifyDataSetChanged();
			}
		});
	}
}
