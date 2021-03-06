package eu.siacs.conversations.ui;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.ImageProvider;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.UIHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.IntentSender.SendIntentException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

public class ConversationActivity extends XmppActivity {

	public static final String VIEW_CONVERSATION = "viewConversation";
	public static final String CONVERSATION = "conversationUuid";
	public static final String TEXT = "text";
	public static final String PRESENCE = "eu.siacs.conversations.presence";

	public static final int REQUEST_SEND_MESSAGE = 0x75441;
	private static final int REQUEST_ATTACH_FILE_DIALOG = 0x48502;
	private static final int REQUEST_IMAGE_CAPTURE = 0x33788;
	
	private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x92734;
	private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x84123;

	protected SlidingPaneLayout spl;

	private List<Conversation> conversationList = new ArrayList<Conversation>();
	private Conversation selectedConversation = null;
	private ListView listView;
	
	private boolean paneShouldBeOpen = true;
	private boolean useSubject = true;
	private ArrayAdapter<Conversation> listAdapter;
	
	public Message pendingMessage = null;

	private OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {

		@Override
		public void onConversationListChanged() {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					updateConversationList();
					if (paneShouldBeOpen) {
						if (conversationList.size() >= 1) {
							swapConversationFragment();
						} else {
							startActivity(new Intent(getApplicationContext(),
									ContactsActivity.class));
							finish();
						}
					}
					ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
							.findFragmentByTag("conversation");
					if (selectedFragment != null) {
						selectedFragment.updateMessages();
					}
				}
			});
		}
	};
	
	protected ConversationActivity activity = this;
	private DisplayMetrics metrics;
	private Toast prepareImageToast;

	public List<Conversation> getConversationList() {
		return this.conversationList;
	}

	public Conversation getSelectedConversation() {
		return this.selectedConversation;
	}
	
	public void setSelectedConversation(Conversation conversation) {
		this.selectedConversation = conversation;
	}

	public ListView getConversationListView() {
		return this.listView;
	}

	public SlidingPaneLayout getSlidingPaneLayout() {
		return this.spl;
	}

	public boolean shouldPaneBeOpen() {
		return paneShouldBeOpen;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		metrics = getResources().getDisplayMetrics();
		
		super.onCreate(savedInstanceState);

		setContentView(R.layout.fragment_conversations_overview);

		listView = (ListView) findViewById(R.id.list);

		this.listAdapter = new ArrayAdapter<Conversation>(this,
				R.layout.conversation_list_row, conversationList) {
			@Override
			public View getView(int position, View view, ViewGroup parent) {
				if (view == null) {
					LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					view = (View) inflater.inflate(
							R.layout.conversation_list_row, null);
				}
				Conversation conv;
				if (conversationList.size() > position) {
					conv = getItem(position);
				} else {
					return view;
				}
				if (!spl.isSlideable()) {
					if (conv == getSelectedConversation()) {
						view.setBackgroundColor(0xffdddddd);
					} else {
						view.setBackgroundColor(Color.TRANSPARENT);
					}
				} else {
					view.setBackgroundColor(Color.TRANSPARENT);
				}
				TextView convName = (TextView) view
						.findViewById(R.id.conversation_name);
				convName.setText(conv.getName(useSubject));
				TextView convLastMsg = (TextView) view
						.findViewById(R.id.conversation_lastmsg);
				ImageView imagePreview = (ImageView) view.findViewById(R.id.conversation_lastimage);
				
				Message latestMessage = conv.getLatestMessage();
				
				if (latestMessage.getType() == Message.TYPE_TEXT) {
					if (latestMessage.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
						convLastMsg.setText(conv.getLatestMessage().getBody());
					} else {
						convLastMsg.setText(getText(R.string.encrypted_message_received));
					}
					convLastMsg.setVisibility(View.VISIBLE);
					imagePreview.setVisibility(View.GONE);
				} else if (latestMessage.getType() == Message.TYPE_IMAGE) {
					if (latestMessage.getStatus() >= Message.STATUS_RECIEVED) {
						convLastMsg.setVisibility(View.GONE);
						imagePreview.setVisibility(View.VISIBLE);
						loadBitmap(latestMessage, imagePreview);
					} else {
						convLastMsg.setVisibility(View.VISIBLE);
						imagePreview.setVisibility(View.GONE);
						if (latestMessage.getStatus() == Message.STATUS_RECEIVED_OFFER) {
							convLastMsg.setText(getText(R.string.image_offered_for_download));
						} else if (latestMessage.getStatus() == Message.STATUS_RECIEVING) {
							convLastMsg.setText(getText(R.string.receiving_image));
						} else {
							convLastMsg.setText("");
						}
					}
				}
				
				

				if (!conv.isRead()) {
					convName.setTypeface(null, Typeface.BOLD);
					convLastMsg.setTypeface(null, Typeface.BOLD);
				} else {
					convName.setTypeface(null, Typeface.NORMAL);
					convLastMsg.setTypeface(null, Typeface.NORMAL);
				}

				((TextView) view.findViewById(R.id.conversation_lastupdate))
						.setText(UIHelper.readableTimeDifference(conv
								.getLatestMessage().getTimeSent()));

				ImageView profilePicture = (ImageView) view
						.findViewById(R.id.conversation_image);
				profilePicture.setImageBitmap(UIHelper.getContactPicture(
						conv, 56, activity.getApplicationContext(), false));
				
				return view;
			}

		};

		listView.setAdapter(this.listAdapter);

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
					int position, long arg3) {
				paneShouldBeOpen = false;
				if (getSelectedConversation() != conversationList.get(position)) {
					setSelectedConversation(conversationList.get(position));
					swapConversationFragment(); // .onBackendConnected(conversationList.get(position));
				} else {
					spl.closePane();
				}
			}
		});
		spl = (SlidingPaneLayout) findViewById(R.id.slidingpanelayout);
		spl.setParallaxDistance(150);
		spl.setShadowResource(R.drawable.es_slidingpane_shadow);
		spl.setSliderFadeColor(0);
		spl.setPanelSlideListener(new PanelSlideListener() {

			@Override
			public void onPanelOpened(View arg0) {
				paneShouldBeOpen = true;
				getActionBar().setDisplayHomeAsUpEnabled(false);
				getActionBar().setTitle(R.string.app_name);
				invalidateOptionsMenu();
				hideKeyboard();
			}

			@Override
			public void onPanelClosed(View arg0) {
				paneShouldBeOpen = false;
				if ((conversationList.size() > 0)
						&& (getSelectedConversation() != null)) {
					getActionBar().setDisplayHomeAsUpEnabled(true);
					getActionBar().setTitle(
							getSelectedConversation().getName(useSubject));
					invalidateOptionsMenu();
					if (!getSelectedConversation().isRead()) {
						getSelectedConversation().markRead();
						UIHelper.updateNotification(getApplicationContext(),
								getConversationList(), null, false);
						listView.invalidateViews();
					}
				}
			}

			@Override
			public void onPanelSlide(View arg0, float arg1) {
				// TODO Auto-generated method stub

			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.conversations, menu);
		MenuItem menuSecure = (MenuItem) menu.findItem(R.id.action_security);
		MenuItem menuArchive = (MenuItem) menu.findItem(R.id.action_archive);
		MenuItem menuMucDetails = (MenuItem) menu
				.findItem(R.id.action_muc_details);
		MenuItem menuContactDetails = (MenuItem) menu
				.findItem(R.id.action_contact_details);
		MenuItem menuInviteContacts = (MenuItem) menu
				.findItem(R.id.action_invite);
		MenuItem menuAttach = (MenuItem) menu.findItem(R.id.action_attach_file);
		MenuItem menuClearHistory = (MenuItem) menu.findItem(R.id.action_clear_history);

		if ((spl.isOpen() && (spl.isSlideable()))) {
			menuArchive.setVisible(false);
			menuMucDetails.setVisible(false);
			menuContactDetails.setVisible(false);
			menuSecure.setVisible(false);
			menuInviteContacts.setVisible(false);
			menuAttach.setVisible(false);
			menuClearHistory.setVisible(false);
		} else {
			((MenuItem) menu.findItem(R.id.action_add)).setVisible(!spl
					.isSlideable());
			if (this.getSelectedConversation() != null) {
				if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
					menuContactDetails.setVisible(false);
					menuSecure.setVisible(false);
					menuAttach.setVisible(false);
				} else {
					menuMucDetails.setVisible(false);
					menuInviteContacts.setVisible(false);
					if (this.getSelectedConversation().getLatestMessage()
							.getEncryption() != Message.ENCRYPTION_NONE) {
						menuSecure.setIcon(R.drawable.ic_action_secure);
					}
				}
			}
		}
		return true;
	}
	
	private void selectPresenceToAttachFile(final int attachmentChoice) {
		selectPresence(getSelectedConversation(), new OnPresenceSelected() {
			
			@Override
			public void onPresenceSelected(boolean success, String presence) {
				if (success) {
					if (attachmentChoice==ATTACHMENT_CHOICE_TAKE_PHOTO) {
						Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
						takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, ImageProvider.getIncomingContentUri());
						if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
							startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
						}
					} else if (attachmentChoice==ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
						Intent attachFileIntent = new Intent();
						attachFileIntent.setType("image/*");
						attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
						Intent chooser = Intent.createChooser(attachFileIntent, getString(R.string.attach_file));
						startActivityForResult(chooser,	REQUEST_ATTACH_FILE_DIALOG);
					}
				}
			}

			@Override
			public void onSendPlainTextInstead() {
				// TODO Auto-generated method stub
				
			}
		},"file");
	}

	private void attachFile(final int attachmentChoice) {
		final Conversation conversation = getSelectedConversation();
		if (getSelectedConversation().getNextEncryption() == Message.ENCRYPTION_NONE) {
			selectPresenceToAttachFile(attachmentChoice);
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Insecure file transfer");
			builder.setMessage("Although this is an OTR session, file transfers will be in plain text");
			builder.setNegativeButton(getString(R.string.cancel), null);
			builder.setPositiveButton(getString(R.string.send_unencrypted), new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					conversation.setNextEncryption(Message.ENCRYPTION_NONE);
					attachFile(attachmentChoice);
				}
			});
			builder.create().show();
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			spl.openPane();
			break;
		case R.id.action_attach_file:
			View menuAttachFile = findViewById(R.id.action_attach_file);
			PopupMenu attachFilePopup = new PopupMenu(this, menuAttachFile);
			attachFilePopup.inflate(R.menu.attachment_choices);
			attachFilePopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
				
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					switch (item.getItemId()) {
					case R.id.attach_choose_picture:
						attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
						break;
					case R.id.attach_take_picture:
						attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
						break;
					}
					return false;
				}
			});
			attachFilePopup.show();
			break;
		case R.id.action_add:
			startActivity(new Intent(this, ContactsActivity.class));
			break;
		case R.id.action_archive:
			this.endConversation(getSelectedConversation());
			break;
		case R.id.action_contact_details:
			Contact contact = this.getSelectedConversation().getContact();
			if (contact != null) {
				Intent intent = new Intent(this, ContactDetailsActivity.class);
				intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
				intent.putExtra("uuid", contact.getUuid());
				startActivity(intent);
			} else {
				showAddToRosterDialog(getSelectedConversation());
			}
			break;
		case R.id.action_muc_details:
			Intent intent = new Intent(this, MucDetailsActivity.class);
			intent.setAction(MucDetailsActivity.ACTION_VIEW_MUC);
			intent.putExtra("uuid", getSelectedConversation().getUuid());
			startActivity(intent);
			break;
		case R.id.action_invite:
			Intent inviteIntent = new Intent(getApplicationContext(),
					ContactsActivity.class);
			inviteIntent.setAction("invite");
			inviteIntent.putExtra("uuid", getSelectedConversation().getUuid());
			startActivity(inviteIntent);
			break;
		case R.id.action_security:
			final Conversation conversation = getSelectedConversation();
			View menuItemView = findViewById(R.id.action_security);
			PopupMenu popup = new PopupMenu(this, menuItemView);
			final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
					.findFragmentByTag("conversation");
			if (fragment != null) {
				popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

					@Override
					public boolean onMenuItemClick(MenuItem item) {
						switch (item.getItemId()) {
						case R.id.encryption_choice_none:
							conversation.setNextEncryption(Message.ENCRYPTION_NONE);
							item.setChecked(true);
							break;
						case R.id.encryption_choice_otr:
							conversation.setNextEncryption(Message.ENCRYPTION_OTR);
							item.setChecked(true);
							break;
						default:
							conversation.setNextEncryption(Message.ENCRYPTION_NONE);
							break;
						}
						fragment.updateChatMsgHint();
						return true;
					}
				});
				popup.inflate(R.menu.encryption_choices);
				switch (conversation.getNextEncryption()) {
				case Message.ENCRYPTION_NONE:
					popup.getMenu().findItem(R.id.encryption_choice_none)
							.setChecked(true);
					break;
				case Message.ENCRYPTION_OTR:
					popup.getMenu().findItem(R.id.encryption_choice_otr)
							.setChecked(true);
					break;
				default:
					popup.getMenu().findItem(R.id.encryption_choice_none)
							.setChecked(true);
					break;
				}
				popup.show();
			}

			break;
		case R.id.action_clear_history:
			clearHistoryDialog(getSelectedConversation());
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void endConversation(Conversation conversation) {
		conversation.setStatus(Conversation.STATUS_ARCHIVED);
		paneShouldBeOpen = true;
		spl.openPane();
		xmppConnectionService.archiveConversation(conversation);
		if (conversationList.size() > 0) {
			setSelectedConversation(conversationList.get(0));
		} else {
			setSelectedConversation(null);
		}
	}

	protected void clearHistoryDialog(final Conversation conversation) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.clear_conversation_history));
		View dialogView = getLayoutInflater().inflate(R.layout.dialog_clear_history, null);
		final CheckBox endConversationCheckBox = (CheckBox) dialogView.findViewById(R.id.end_conversation_checkbox);
		builder.setView(dialogView);
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.delete_messages), new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				activity.xmppConnectionService.clearConversationHistory(conversation);
				if (endConversationCheckBox.isChecked()) {
					endConversation(conversation);
				}
			}
		});
		builder.create().show();
	}

	protected ConversationFragment swapConversationFragment() {
		ConversationFragment selectedFragment = new ConversationFragment();

		FragmentTransaction transaction = getFragmentManager()
				.beginTransaction();
		transaction.replace(R.id.selected_conversation, selectedFragment,
				"conversation");
		transaction.commit();
		return selectedFragment;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (!spl.isOpen()) {
				spl.openPane();
				return false;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onStart() {
		super.onStart();
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		this.useSubject = preferences.getBoolean("use_subject_in_muc", true);
		if (this.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
		if (conversationList.size() >= 1) {
			onConvChanged.onConversationListChanged();
		}
	}

	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnConversationListChangedListener();
		}
		super.onStop();
	}

	@Override
	void onBackendConnected() {
		this.registerListener();
		if (conversationList.size() == 0) {
			updateConversationList();
		}

		if ((getIntent().getAction() != null)
				&& (getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
			if (getIntent().getType().equals(
					ConversationActivity.VIEW_CONVERSATION)) {
				handledViewIntent = true;

				String convToView = (String) getIntent().getExtras().get(
						CONVERSATION);

				for (int i = 0; i < conversationList.size(); ++i) {
					if (conversationList.get(i).getUuid().equals(convToView)) {
						setSelectedConversation(conversationList.get(i));
					}
				}
				paneShouldBeOpen = false;
				String text = getIntent().getExtras().getString(TEXT, null);
				swapConversationFragment().setText(text);
			}
		} else {
			if (xmppConnectionService.getAccounts().size() == 0) {
				startActivity(new Intent(this, ManageAccountActivity.class));
				finish();
			} else if (conversationList.size() <= 0) {
				// add no history
				startActivity(new Intent(this, ContactsActivity.class));
				finish();
			} else {
				spl.openPane();
				// find currently loaded fragment
				ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
						.findFragmentByTag("conversation");
				if (selectedFragment != null) {
					selectedFragment.onBackendConnected();
				} else {
					setSelectedConversation(conversationList.get(0));
					swapConversationFragment();
				}
				ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
			}
		}
	}

	public void registerListener() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService
					.setOnConversationListChangedListener(this.onConvChanged);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
				attachImageToConversation(getSelectedConversation(),data.getData());
			} else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
				attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
			} else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
				attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
			} else if (requestCode == REQUEST_IMAGE_CAPTURE) {
				attachImageToConversation(getSelectedConversation(), null);
			} else {
				Log.d(LOGTAG,"unknown result code:"+requestCode);
			}
		}
	}
	
	private void attachImageToConversation(Conversation conversation, Uri uri) {
		prepareImageToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
		prepareImageToast.show();
		pendingMessage = xmppConnectionService.attachImageToConversation(conversation, uri, new UiCallback() {
				
			@Override
			public void userInputRequried(PendingIntent pi) {
				hidePrepareImageToast();
			}
			
			@Override
			public void success() {
				sendPendingImageMessage();
				hidePrepareImageToast();
			}
			
			@Override
			public void error(int error) {
				hidePrepareImageToast();
				pendingMessage = null;
				displayErrorDialog(error);
			}
		});
	}
	
	private void hidePrepareImageToast() {
		if (prepareImageToast!=null) {
			runOnUiThread(new Runnable() {
				
				@Override
				public void run() {
					prepareImageToast.cancel();
				}
			});
		}
	}

	private void sendPendingImageMessage() {
		pendingMessage.getConversation().getMessages().add(pendingMessage);
		xmppConnectionService.databaseBackend.createMessage(pendingMessage);
		xmppConnectionService.sendMessage(pendingMessage, null);
		xmppConnectionService.updateUi(pendingMessage.getConversation(), false);
		pendingMessage = null;
	}
	
	public void updateConversationList() {
		conversationList.clear();
		conversationList.addAll(xmppConnectionService.getConversations());
		listView.invalidateViews();
	}
	
	public void selectPresence(final Conversation conversation, final OnPresenceSelected listener, String reason) {
		Account account = conversation.getAccount();
		if (account.getStatus() != Account.STATUS_ONLINE) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.not_connected));
			builder.setIconAttribute(android.R.attr.alertDialogIcon);
			if ("otr".equals(reason)) {
				builder.setMessage(getString(R.string.you_are_offline,getString(R.string.otr_messages)));
			} else if ("file".equals(reason)) {
				builder.setMessage(getString(R.string.you_are_offline,getString(R.string.files)));
			} else {
				builder.setMessage(getString(R.string.you_are_offline_blank));
			}
			builder.setNegativeButton(getString(R.string.cancel), null);
			builder.setPositiveButton(getString(R.string.manage_account), new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					startActivity(new Intent(activity, ManageAccountActivity.class));
				}
			});
			builder.create().show();
			listener.onPresenceSelected(false, null);
		} else {
			Contact contact = conversation.getContact();
			if (contact==null) {
				showAddToRosterDialog(conversation);
				listener.onPresenceSelected(false,null);
			} else {
				Hashtable<String, Integer> presences = contact.getPresences();
				if (presences.size() == 0) {
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle(getString(R.string.contact_offline));
					if ("otr".equals(reason)) {
						builder.setMessage(getString(R.string.contact_offline_otr));
						builder.setPositiveButton(getString(R.string.send_unencrypted), new OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								listener.onSendPlainTextInstead();
							}
						});
					} else if ("file".equals(reason)) {
						builder.setMessage(getString(R.string.contact_offline_file));
					}
					builder.setIconAttribute(android.R.attr.alertDialogIcon);
					builder.setNegativeButton(getString(R.string.cancel), null);
					builder.create().show();
					listener.onPresenceSelected(false, null);
				} else if (presences.size() == 1) {
					String presence = (String) presences.keySet().toArray()[0];
					conversation.setNextPresence(presence);
					listener.onPresenceSelected(true, presence);
				} else {
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle(getString(R.string.choose_presence));
					final String[] presencesArray = new String[presences.size()];
					presences.keySet().toArray(presencesArray);
					builder.setItems(presencesArray,
							new DialogInterface.OnClickListener() {
	
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									String presence = presencesArray[which];
									conversation.setNextPresence(presence);
									listener.onPresenceSelected(true,presence);
								}
							});
					builder.create().show();
				}
			}
		}
	}
	
	private void showAddToRosterDialog(final Conversation conversation) {
		String jid = conversation.getContactJid();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(jid);
		builder.setMessage(getString(R.string.not_in_roster));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.add_contact), new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				String jid = conversation.getContactJid();
				Account account = getSelectedConversation().getAccount();
				String name = jid.split("@")[0];
				Contact contact = new Contact(account, name, jid, null);
				xmppConnectionService.createContact(contact);
			}
		});
		builder.create().show();
	}
	
	public void runIntent(PendingIntent pi, int requestCode) {
		try {
			this.startIntentSenderForResult(pi.getIntentSender(),requestCode, null, 0,
					0, 0);
		} catch (SendIntentException e1) {
			Log.d("xmppService","failed to start intent to send message");
		}
	}
	
	
	class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
	    private final WeakReference<ImageView> imageViewReference;
	    private Message message = null;

	    public BitmapWorkerTask(ImageView imageView) {
	        imageViewReference = new WeakReference<ImageView>(imageView);
	    }

	    @Override
	    protected Bitmap doInBackground(Message... params) {
	        message = params[0];
	        try {
				return xmppConnectionService.getFileBackend().getThumbnail(message, (int) (metrics.density * 288),false);
			} catch (FileNotFoundException e) {
				Log.d("xmppService","file not found!");
				return null;
			}
	    }

	    @Override
	    protected void onPostExecute(Bitmap bitmap) {
	        if (imageViewReference != null && bitmap != null) {
	            final ImageView imageView = imageViewReference.get();
	            if (imageView != null) {
	                imageView.setImageBitmap(bitmap);
	                imageView.setBackgroundColor(0x00000000);
	            }
	        }
	    }
	}
	
	public void loadBitmap(Message message, ImageView imageView) {
		Bitmap bm;
		try {
			bm = xmppConnectionService.getFileBackend().getThumbnail(message, (int) (metrics.density * 288), true);
		} catch (FileNotFoundException e) {
			bm = null;
		}
		if (bm!=null) {
			imageView.setImageBitmap(bm);
			imageView.setBackgroundColor(0x00000000);
		} else {
		    if (cancelPotentialWork(message, imageView)) {
		    	imageView.setBackgroundColor(0xff333333);
		        final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
		        final AsyncDrawable asyncDrawable =
		                new AsyncDrawable(getResources(), null, task);
		        imageView.setImageDrawable(asyncDrawable);
		        task.execute(message);
		    }
		}
	}
	
	public static boolean cancelPotentialWork(Message message, ImageView imageView) {
	    final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

	    if (bitmapWorkerTask != null) {
	        final Message oldMessage = bitmapWorkerTask.message;
	        if (oldMessage == null || message != oldMessage) {
	            bitmapWorkerTask.cancel(true);
	        } else {
	            return false;
	        }
	    }
	    return true;
	}
	
	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		   if (imageView != null) {
		       final Drawable drawable = imageView.getDrawable();
		       if (drawable instanceof AsyncDrawable) {
		           final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
		           return asyncDrawable.getBitmapWorkerTask();
		       }
		    }
		    return null;
	}
	
	static class AsyncDrawable extends BitmapDrawable {
	    private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

	    public AsyncDrawable(Resources res, Bitmap bitmap,
	            BitmapWorkerTask bitmapWorkerTask) {
	        super(res, bitmap);
	        bitmapWorkerTaskReference =
	            new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
	    }

	    public BitmapWorkerTask getBitmapWorkerTask() {
	        return bitmapWorkerTaskReference.get();
	    }
	}
}
