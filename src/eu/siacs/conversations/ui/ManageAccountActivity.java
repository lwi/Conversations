package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;
import java.security.cert.X509Certificate;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.EditAccount.EditAccountListener;
import eu.siacs.conversations.xmpp.OnTLSExceptionReceived;
import eu.siacs.conversations.xmpp.XmppConnection;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

public class ManageAccountActivity extends XmppActivity {
	
	protected boolean isActionMode = false;
	protected ActionMode actionMode;
	protected Account selectedAccountForActionMode = null;
	protected ManageAccountActivity activity = this;
	
	protected boolean firstrun = true;
	
	protected List<Account> accountList = new ArrayList<Account>();
	protected ListView accountListView;
	protected ArrayAdapter<Account> accountListViewAdapter;
	protected OnAccountListChangedListener accountChanged = new OnAccountListChangedListener() {

		@Override
		public void onAccountListChangedListener() {
			accountList.clear();
			accountList.addAll(xmppConnectionService.getAccounts());
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					accountListViewAdapter.notifyDataSetChanged();
				}
			});
		}
	};

/*
	private static class ViewHolder
	{	TextView mSubjectTextView;
		RadioButton mRadioButton;
	}
*/

	private class CertificateAdapter extends BaseAdapter
	{	private final X509Certificate[] mCerts;

		private CertificateAdapter (final X509Certificate[] certs)
		{	mCerts = certs;
		}

		@Override
		public int
		getCount ()
		{	return mCerts.length;
		}

		@Override
		public X509Certificate
		getItem (int adapterPosition)
		{	return mCerts[adapterPosition];
		}

		@Override
		public long
		getItemId (int adapterPosition)
		{	return adapterPosition;
		}

		// TODO: move layout to XML
		// TODO: radio buttons please
		// TODO: also display fingerprint
		@Override
		public View
		getView (final int adapterPosition, View view, ViewGroup parent)
		{	//ViewHolder holder;
			if (view == null)
			{ /*LinearLayout ll = new LinearLayout (ManageAccountActivity.this);
				holder = new ViewHolder ();
				holder.mRadioButton = new RadioButton (ManageAccountActivity.this);
				ll.addView (holder.mRadioButton);
				ll.addView (holder.mSubjectTextView); */
				view = (View) new TextView (ManageAccountActivity.this);
				/*holder.mSubjectTextView = (TextView) view;
				view.setTag (holder);*/
			}
			//else holder = (ViewHolder) view.getTag ();

			final String subjectName = mCerts[adapterPosition].getSubjectDN ().getName ();
			//holder.mSubjectTextView.setText (subjectName);
			((TextView) view).setText (subjectName);

/*
			final ListView lv = (ListView) parent;
			final int listViewCheckedItemPosition = lv.getCheckedItemPosition ();
			final int adapterCheckedItemPosition = listViewCheckedItemPosition - 1;
			holder.mRadioButton.setChecked(adapterPosition == adapterCheckedItemPosition);
*/
			return view;
		}
  }

	protected OnTLSExceptionReceived tlsExceptionReceived = new OnTLSExceptionReceived() {
		
		@Override
		public void onTLSExceptionReceived(final X509Certificate[] chain, final Account account) {
			activity.runOnUiThread(new Runnable() {
				
				@Override
				public void run() {
					AlertDialog.Builder builder = new AlertDialog.Builder(activity);
					builder.setTitle("Untrusted Certificate");
					builder.setIconAttribute(android.R.attr.alertDialogIcon);
					final ListView lv = new ListView (activity);
					lv.setChoiceMode (android.widget.AbsListView.CHOICE_MODE_SINGLE);
					final TextView hint = new TextView (activity);
					if (account.getTrustedCertificate () != null)
						hint.setText ("Your trusted certificate is not part of the chain the server provided");
					else
						hint.setText ("Please select the certificate of the chain which you trust");
					lv.addHeaderView (hint);
					final CertificateAdapter adapter = new CertificateAdapter (chain);
					lv.setAdapter (adapter);
					lv.setOnItemClickListener (new AdapterView.OnItemClickListener ()
					{	public void
						onItemClick (AdapterView<?> parent, View view, int position, long id)
						{ lv.setItemChecked (position, true);
						}
					});
					builder.setView (lv);

					builder.setNegativeButton("Abort", null);
					builder.setPositiveButton("Trust selected certificate", new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							final int pos = lv.getCheckedItemPosition ();
							if (pos == android.widget.AdapterView.INVALID_POSITION) return;
							Log.d(LOGTAG, "adding cert to trust store ...");
							account.setTrustedCertificate (adapter.getItem (pos - 1));
							activity.xmppConnectionService.updateAccount(account);
						}
					});
					builder.create().show();
				}
			});
			
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.manage_accounts);

		accountListView = (ListView) findViewById(R.id.account_list);
		accountListViewAdapter = new ArrayAdapter<Account>(
				getApplicationContext(), R.layout.account_row, this.accountList) {
			@Override
			public View getView(int position, View view, ViewGroup parent) {
				Account account = getItem(position);
				if (view == null) {
					LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					view = (View) inflater.inflate(R.layout.account_row, null);
				}
				((TextView) view.findViewById(R.id.account_jid))
						.setText(account.getJid());
				TextView statusView = (TextView) view
						.findViewById(R.id.account_status);
				switch (account.getStatus()) {
				case Account.STATUS_DISABLED:
					statusView.setText("temporarily disabled");
					statusView.setTextColor(0xFF1da9da);
					break;
				case Account.STATUS_ONLINE:
					statusView.setText("online");
					statusView.setTextColor(0xFF83b600);
					break;
				case Account.STATUS_CONNECTING:
					statusView.setText("connecting\u2026");
					statusView.setTextColor(0xFF1da9da);
					break;
				case Account.STATUS_OFFLINE:
					statusView.setText("offline");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_UNAUTHORIZED:
					statusView.setText("unauthorized");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_SERVER_NOT_FOUND:
					statusView.setText("server not found");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_NO_INTERNET:
					statusView.setText("no internet");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_SERVER_REQUIRES_TLS:
					statusView.setText("server requires TLS");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_TLS_ERROR:
					statusView.setText("untrusted cerficate");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_REGISTRATION_FAILED:
					statusView.setText("registration failed");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_REGISTRATION_CONFLICT:
					statusView.setText("username already in use");
					statusView.setTextColor(0xFFe92727);
					break;
				case  Account.STATUS_REGISTRATION_SUCCESSFULL:
					statusView.setText("registration completed");
					statusView.setTextColor(0xFF83b600);
					break;
				case Account.STATUS_REGISTRATION_NOT_SUPPORTED:
					statusView.setText("server does not support registration");
					statusView.setTextColor(0xFFe92727);
					break;
				default:
					statusView.setText("");
					break;
				}

				return view;
			}
		};
		final XmppActivity activity = this;
		accountListView.setAdapter(this.accountListViewAdapter);
		accountListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view,
					int position, long arg3) {
				if (!isActionMode) {
					Account account = accountList.get(position);
					if ((account.getStatus() == Account.STATUS_OFFLINE)||(account.getStatus() == Account.STATUS_TLS_ERROR)) {
						activity.xmppConnectionService.reconnectAccount(accountList.get(position),true);
					} else if (account.getStatus() == Account.STATUS_ONLINE) {
						activity.startActivity(new Intent(activity.getApplicationContext(),ContactsActivity.class));
					} else if (account.getStatus() != Account.STATUS_DISABLED) {
						editAccount(account);
					}
				} else {
					selectedAccountForActionMode = accountList.get(position);
					actionMode.invalidate();
				}
			}
		});
		accountListView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View view,
					int position, long arg3) {
				if (!isActionMode) {
					accountListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
					accountListView.setItemChecked(position,true);
					selectedAccountForActionMode = accountList.get(position);
					actionMode = activity.startActionMode((new ActionMode.Callback() {
						
						@Override
						public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
							if (selectedAccountForActionMode.isOptionSet(Account.OPTION_DISABLED)) {
					        	menu.findItem(R.id.mgmt_account_enable).setVisible(true);
					        	menu.findItem(R.id.mgmt_account_disable).setVisible(false);
					        } else {
					        	menu.findItem(R.id.mgmt_account_disable).setVisible(true);
					        	menu.findItem(R.id.mgmt_account_enable).setVisible(false);
					        }
								menu.findItem (R.id.mgmt_tls_cert).setVisible
									(selectedAccountForActionMode.getTrustedCertificate() != null);
							return true;
						}
						
						@Override
						public void onDestroyActionMode(ActionMode mode) {
							// TODO Auto-generated method stub
							
						}
						
						@Override
						public boolean onCreateActionMode(ActionMode mode, Menu menu) {
							MenuInflater inflater = mode.getMenuInflater();
					        inflater.inflate(R.menu.manageaccounts_context, menu);
							return true;
						}
						
						@Override
						public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
							if (item.getItemId()==R.id.mgmt_account_edit) {
								editAccount(selectedAccountForActionMode);
							} else if (item.getItemId()==R.id.mgmt_account_disable) {
								selectedAccountForActionMode.setOption(Account.OPTION_DISABLED, true);
								xmppConnectionService.updateAccount(selectedAccountForActionMode);
								mode.finish();
							} else if (item.getItemId()==R.id.mgmt_account_enable) {
								selectedAccountForActionMode.setOption(Account.OPTION_DISABLED, false);
								xmppConnectionService.updateAccount(selectedAccountForActionMode);
								mode.finish();
							} else if (item.getItemId()==R.id.mgmt_account_delete) {
								AlertDialog.Builder builder = new AlertDialog.Builder(activity);
								builder.setTitle("Are you sure?");
								builder.setIconAttribute(android.R.attr.alertDialogIcon);
								builder.setMessage("If you delete your account your entire conversation history will be lost");
								builder.setPositiveButton("Delete", new OnClickListener() {
									
									@Override
									public void onClick(DialogInterface dialog, int which) {
										xmppConnectionService.deleteAccount(selectedAccountForActionMode);
										selectedAccountForActionMode = null;
										mode.finish();
									}
								});
								builder.setNegativeButton("Cancel",null);
								builder.create().show();
							} else if (item.getItemId() == R.id.mgmt_otr_key) {
								AlertDialog.Builder builder = new AlertDialog.Builder(activity);
								builder.setTitle("OTR Fingerprint");
								String fingerprintTxt = selectedAccountForActionMode.getOtrFingerprint(getApplicationContext());
								View view = (View) getLayoutInflater().inflate(R.layout.otr_fingerprint, null);
								if (fingerprintTxt!=null) {
									TextView fingerprint = (TextView) view.findViewById(R.id.otr_fingerprint);
									TextView noFingerprintView = (TextView) view.findViewById(R.id.otr_no_fingerprint);
									fingerprint.setText(fingerprintTxt);
									fingerprint.setVisibility(View.VISIBLE);
									noFingerprintView.setVisibility(View.GONE);
								}
								builder.setView(view);
								builder.setPositiveButton("Done", null);
								builder.create().show();
							} else if (item.getItemId() == R.id.mgmt_tls_cert) {
								final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
								builder.setTitle("Trusted TLS certificate");
								final TextView tv = new TextView (activity);
								final X509Certificate cert = selectedAccountForActionMode.getTrustedCertificate ();
								tv.setMovementMethod(new ScrollingMovementMethod());
								tv.setLayoutParams(new ViewGroup.LayoutParams(
								 ViewGroup.LayoutParams.FILL_PARENT,
								 ViewGroup.LayoutParams.FILL_PARENT));
								tv.setText (cert.toString ());
								builder.setView (tv);
								builder.setPositiveButton ("Still trust", null);
								builder.setNegativeButton ("Reject trust", new OnClickListener () {
									@Override
									public void
									onClick (final DialogInterface dialog, final int which)
									{	selectedAccountForActionMode.setTrustedCertificate (null);
									}
								});
								builder.create ().show ();
							} else if (item.getItemId() == R.id.mgmt_account_info) {
								AlertDialog.Builder builder = new AlertDialog.Builder(activity);
								builder.setTitle(getString(R.string.account_info));
								if (selectedAccountForActionMode.getStatus() == Account.STATUS_ONLINE) {
									XmppConnection xmpp = selectedAccountForActionMode.getXmppConnection();
									long connectionAge = (SystemClock.elapsedRealtime() - xmpp.lastConnect) / 60000;
									long sessionAge = (SystemClock.elapsedRealtime() - xmpp.lastSessionStarted) / 60000;
									long connectionAgeHours = connectionAge / 60;
									long sessionAgeHours = sessionAge / 60;
									View view = (View) getLayoutInflater().inflate(R.layout.server_info, null);
									TextView connection = (TextView) view.findViewById(R.id.connection);
									TextView session = (TextView) view.findViewById(R.id.session);
									TextView pcks_sent = (TextView) view.findViewById(R.id.pcks_sent);
									TextView pcks_received = (TextView) view.findViewById(R.id.pcks_received);
									TextView carbon = (TextView) view.findViewById(R.id.carbon);
									TextView stream = (TextView) view.findViewById(R.id.stream);
									TextView roster = (TextView) view.findViewById(R.id.roster);
									TextView presences = (TextView) view.findViewById(R.id.number_presences);
									presences.setText(selectedAccountForActionMode.countPresences()+"");
									pcks_received.setText(""+xmpp.getReceivedStanzas());
									pcks_sent.setText(""+xmpp.getSentStanzas());
									if (connectionAgeHours >= 2) {
										connection.setText(connectionAgeHours+" hours");
									} else {
										connection.setText(connectionAge+" mins");
									}
									if (xmpp.hasFeatureStreamManagment()) {
										if (sessionAgeHours >= 2) {
											session.setText(sessionAgeHours+" hours");
										} else {
											session.setText(sessionAge+" mins");
										}
										stream.setText("Yes");
									} else {
										stream.setText("No");
										session.setText(connection.getText());
									}
									if (xmpp.hasFeaturesCarbon()) {
										carbon.setText("Yes");
									} else {
										carbon.setText("No");
									}
									if (xmpp.hasFeatureRosterManagment()) {
										roster.setText("Yes");
									} else {
										roster.setText("No");
									}
									builder.setView(view);
								} else {
									builder.setMessage("Account is offline");
								}
								builder.setPositiveButton("Hide", null);
								builder.create().show();
							}
							return true;
						}

						
					}));
					return true;
				} else {
					return false;
				}
			}
		});
	}
	
	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnAccountListChangedListener();
			xmppConnectionService.removeOnTLSExceptionReceivedListener();
		}
		super.onStop();
	}

	@Override
	void onBackendConnected() {
		xmppConnectionService.setOnAccountListChangedListener(accountChanged);
		xmppConnectionService.setOnTLSExceptionReceivedListener(tlsExceptionReceived);
		this.accountList.clear();
		this.accountList.addAll(xmppConnectionService.getAccounts());
		accountListViewAdapter.notifyDataSetChanged();
		if ((this.accountList.size() == 0)&&(this.firstrun)) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			addAccount();
			this.firstrun = false;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.manageaccounts, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_add_account:
			addAccount();
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void editAccount(Account account) {
			EditAccount dialog = new EditAccount();
			dialog.setAccount(account);
			dialog.setEditAccountListener(new EditAccountListener() {

				@Override
				public void onAccountEdited(Account account) {
					xmppConnectionService.updateAccount(account);
					if (actionMode != null) { 
						actionMode.finish();
					}
				}
			});
			dialog.show(getFragmentManager(), "edit_account");
		
	}
	
	protected void addAccount() {
		final Activity activity = this;
		EditAccount dialog = new EditAccount();
		dialog.setEditAccountListener(new EditAccountListener() {

			@Override
			public void onAccountEdited(Account account) {
				xmppConnectionService.createAccount(account);
				activity.getActionBar().setDisplayHomeAsUpEnabled(true);
			}
		});
		dialog.show(getFragmentManager(), "add_account");
	}

	
	@Override
	public void onActionModeStarted(ActionMode mode) {
		super.onActionModeStarted(mode);
		this.isActionMode = true;
	}
	
	@Override
	public void onActionModeFinished(ActionMode mode) {
		super.onActionModeFinished(mode);
		this.isActionMode = false;
		accountListView.clearChoices();
		accountListView.requestLayout();
		accountListView.post(new Runnable() {
            @Override
            public void run() {
                accountListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
            }
        });
	}
	
	 @Override
	 protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		 super.onActivityResult(requestCode, resultCode, data);
	 }
}
