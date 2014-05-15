package eu.siacs.conversations.xmpp;

import java.security.cert.X509Certificate;
import eu.siacs.conversations.entities.Account;

public interface OnTLSExceptionReceived {
	public void onTLSExceptionReceived(X509Certificate[] chain, Account account);
}
