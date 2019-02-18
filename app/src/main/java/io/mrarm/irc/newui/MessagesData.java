package io.mrarm.irc.newui;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

import io.mrarm.chatlib.ResponseCallback;
import io.mrarm.chatlib.dto.MessageFilterOptions;
import io.mrarm.chatlib.dto.MessageId;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageList;
import io.mrarm.chatlib.dto.MessageListAfterIdentifier;
import io.mrarm.chatlib.message.MessageStorageApi;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.util.TwoWayList;

public class MessagesData {

    private static final int ITEMS_ON_SCREEN = 100;

    private final Context mContext;
    private final ServerConnectionInfo mConnection;
    private final MessageStorageApi mSource;
    private final String mChannel;
    private Listener mListener;

    private final TwoWayList<Item> mItems = new TwoWayList<>();
    private MessageFilterOptions mFilterOptions;
    private MessageListAfterIdentifier mNewerMessages;
    private MessageListAfterIdentifier mOlderMessages;
    private CancellableMessageListCallback mLoadingMessages;

    public MessagesData(Context context,  ServerConnectionInfo connection, String channel) {
        mContext = context;
        mConnection = connection;
        mSource = connection.getApiInstance().getMessageStorageApi();
        mChannel = channel;
    }

    public ServerConnectionInfo getConnection() {
        return mConnection;
    }

    public String getChannel() {
        return mChannel;
    }

    public Item get(int i) {
        return mItems.get(i);
    }

    public int size() {
        return mItems.size();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void load(MessageFilterOptions filterOptions) {
        synchronized (this) {
            mFilterOptions = filterOptions;
            mNewerMessages = null;
            mOlderMessages = null;
            if (mLoadingMessages != null)
                mLoadingMessages.setCancelled();
            mLoadingMessages = new CancellableMessageListCallback() {
                @Override
                public void onResponse(MessageList l) {
                    synchronized (MessagesData.this) {
                        if (isCancelled())
                            return;
                        mNewerMessages = l.getNewer();
                        mOlderMessages = l.getOlder();
                        setMessages(l.getMessages(), l.getMessageIds());
                    }
                }
            };
        }
        mSource.getMessages(mChannel, ITEMS_ON_SCREEN, filterOptions, null, mLoadingMessages,
                (e) -> {
                    Toast.makeText(mContext, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    Log.e("MessagesData", "Failed to load messages");
                    e.printStackTrace();
                });
    }

    public synchronized boolean loadMoreMessages(boolean newer) {
        MessageListAfterIdentifier after = newer ? mNewerMessages : mOlderMessages;
        if (after == null || mLoadingMessages != null)
            return false;
        mLoadingMessages = new CancellableMessageListCallback() {
            @Override
            public void onResponse(MessageList l) {
                synchronized (MessagesData.this) {
                    if (isCancelled())
                        return;
                    if (newer)
                        appendMessages(l.getMessages(), l.getMessageIds());
                    // TODO: older
                }
            }
        };
        mSource.getMessages(mChannel, ITEMS_ON_SCREEN, mFilterOptions, after,
                mLoadingMessages, null);
        mOlderMessages = null;
        mNewerMessages = null;
        return true;
    }

    private int appendMessageInternal(MessageInfo m, MessageId mi) {
        mItems.addLast(new MessageItem(m, mi));
        return 1;
    }

    private void appendMessage(MessageInfo m, MessageId mi) {
        int pos = mItems.size();
        int cnt = appendMessageInternal(m, mi);
        mListener.onItemsAdded(pos, cnt);
    }

    private void appendMessages(List<MessageInfo> m, List<MessageId> mi) {
        int pos = mItems.size();
        int cnt = 0;
        for (int i = 0; i < m.size(); i++)
            cnt += appendMessageInternal(m.get(i), mi.get(i));
        mListener.onItemsAdded(pos, cnt);
    }

    private void setMessages(List<MessageInfo> m, List<MessageId> mi) {
        mItems.clear();
        for (int i = 0; i < m.size(); i++)
            appendMessageInternal(m.get(i), mi.get(i));
        mListener.onReloaded();
    }


    public static class Item {
    }

    public static class MessageItem extends Item {

        private final MessageInfo mMessage;
        private final MessageId mMessageId;

        public MessageItem(MessageInfo message, MessageId messageId) {
            mMessage = message;
            mMessageId = messageId;
        }

        public MessageInfo getMessage() {
            return mMessage;
        }

        public MessageId getMessageId() {
            return mMessageId;
        }

    }

    public interface Listener {

        /**
         * The initial message list has been reloaded and items may have been reordered arbitrarily.
         */
        void onReloaded();

        /**
         * Called when nwe items have been added at the specified position. This might also be
         * called for a single new item.
         * @param pos the position at which the items has been added.
         * @param count the number of added items
         */
        void onItemsAdded(int pos, int count);

    }

    private abstract class CancellableMessageListCallback implements ResponseCallback<MessageList> {

        private boolean mCancelled = false;

        public boolean isCancelled() {
            synchronized (MessagesData.this) {
                return mCancelled;
            }
        }

        public void setCancelled() {
            synchronized (MessagesData.this) {
                mCancelled = true;
            }
        }

    }

}
