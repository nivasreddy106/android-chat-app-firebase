package com.raffler.app.classes;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.raffler.app.interfaces.UnreadMessageListener;
import com.raffler.app.interfaces.UserValueListener;
import com.raffler.app.models.Chat;
import com.raffler.app.models.Message;
import com.raffler.app.models.MessageStatus;
import com.raffler.app.models.User;
import com.raffler.app.utils.References;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Ghost on 14/8/2017.
 */

public class AppManager {
    private static final AppManager ourInstance = new AppManager();

    public static AppManager getInstance() {
        return ourInstance;
    }

    private Context context;
    private DatabaseReference userRef;
    private ValueEventListener trackUserListener;
    private UserValueListener userValueListener;

    public Chat selectedChat;
    public String userId;

    private AppManager() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        userRef = database.getReference("Users");
    }

    public void trackUser(String uid) {
        trackUserListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null){
                    Map<String, Object> userData = (Map<String, Object>) dataSnapshot.getValue();
                    User user = new User(userData);
                    AppManager.saveSession(context, user);
                    if (userValueListener != null) {
                        userValueListener.onLoadedUser(user);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("TrackUser", databaseError.toString());
            }
        };
        userRef.child(uid).addValueEventListener(trackUserListener);
    }

    public void stopTrackUser(String uid){
        userRef.child(uid).removeEventListener(trackUserListener);
    }

    public static void getUser(String userId, final UserValueListener listener) {
        Query query = References.getInstance().usersRef.child(userId);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null){
                    Map<String, Object> userData = (Map<String, Object>) dataSnapshot.getValue();
                    User user = new User(userData);
                    if (listener != null) {
                        listener.onLoadedUser(user);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("TrackUser", databaseError.toString());
            }
        });
    }

    public static void getUnreadMessageCount(final String chatId, final UnreadMessageListener listener){

        Query queryUnreadMessages = References.getInstance().messagesRef.child(chatId).orderByChild("status").equalTo(MessageStatus.DELIVERED.ordinal());
        queryUnreadMessages.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    int unread_count = 0;
                    for (DataSnapshot child : dataSnapshot.getChildren()) {
                        Map<String, Object> messageData = (Map<String, Object>) child.getValue();
                        Message message = new Message(messageData);
                        if (!message.getSenderId().equals(AppManager.getInstance().userId)) {
                            unread_count += 1;
                        }
                    }

                    if (listener != null) {
                        listener.onUnreadMessages(chatId, unread_count);
                    }
                } else {
                    if (listener != null) {
                        listener.onUnreadMessages(chatId, 0);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("ChatListAdapter", databaseError.toString());
            }
        });

    }

    public static void saveSession(Context context, User user){
        SharedPreferences sharedPreferences = context.getSharedPreferences("AppSession", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("uid", user.getIdx());
        editor.putString("bio", user.getBio());
        editor.putString("name", user.getName());
        editor.putString("photo", user.getPhoto());
        editor.putString("phone", user.getPhone());
        editor.putString("pushToken", user.getPushToken());
        editor.putInt("userStatus", user.getUserStatus().ordinal());
        editor.putInt("userAction", user.getUserAction().ordinal());
        Gson gson = new Gson();
        String chatsDic = gson.toJson(user.getChats());
        editor.putString("chats", chatsDic);

        editor.commit();
    }

    public static User getSession(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("AppSession", Context.MODE_PRIVATE);
        String uid = sharedPreferences.getString("uid", null);
        String name = sharedPreferences.getString("name", "?");
        String photo = sharedPreferences.getString("photo", "?");
        String phone = sharedPreferences.getString("phone", "");
        String bio = sharedPreferences.getString("bio", "?");
        String pushToken = sharedPreferences.getString("pushToken", "?");
        int userStatus = sharedPreferences.getInt("userStatus", 0);
        int userAction = sharedPreferences.getInt("userAction", 0);
        String chatsDic = sharedPreferences.getString("chats", null);
        Map<String,Object> chats = new Gson().fromJson(chatsDic, new TypeToken<Map<String, Object>>(){}.getType());
        if (uid != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("uid", uid);
            data.put("name", name);
            data.put("photo", photo);
            data.put("phone", phone);
            data.put("bio", bio);
            data.put("pushToken", pushToken);
            data.put("userStatus", userStatus);
            data.put("userAction", userAction);
            data.put("chats", (chats != null) ? chats : new HashMap<String, Object>());
            User user = new User(data);
            return user;
        } else {
            return null;
        }
    }

    public static void deleteSession(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences("AppSession", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("uid", null);
        editor.commit();
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setUserValueListener(UserValueListener userValueListener) {
        this.userValueListener = userValueListener;
    }
}
