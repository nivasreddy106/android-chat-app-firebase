package com.raffler.app;

import android.content.Intent;
import android.graphics.Color;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.matrixxun.starry.badgetextview.MenuItemBadge;
import com.onesignal.OSNotification;
import com.onesignal.OSNotificationOpenResult;
import com.onesignal.OSPermissionObserver;
import com.onesignal.OSPermissionStateChanges;
import com.onesignal.OneSignal;
import com.raffler.app.adapters.ViewPagerAdapter;
import com.raffler.app.classes.AppManager;
import com.raffler.app.fragments.ChatListFragment;
import com.raffler.app.fragments.ContactsFragment;
import com.raffler.app.fragments.RafflesFragment;
import com.raffler.app.interfaces.ChatItemClickListener;
import com.raffler.app.interfaces.NewsValueListener;
import com.raffler.app.interfaces.UnreadMessageListener;
import com.raffler.app.interfaces.UserValueListener;
import com.raffler.app.models.Chat;
import com.raffler.app.models.News;
import com.raffler.app.models.User;
import com.raffler.app.models.UserStatus;
import com.raffler.app.utils.References;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements ChatItemClickListener, UnreadMessageListener, UserValueListener, NewsValueListener{

    private static final String TAG = "MainActivity";

    private TabLayout tabLayout;
    private ViewPager viewPager;

    private MenuItem menuItemRefresh, menuItemNews, menuItemPoints;

    //Fragments
    private RafflesFragment rafflesFragment;
    private ChatListFragment chatFragment;
    private ContactsFragment contactsFragment;

    private DatabaseReference userStatusRef;

    private String[] tabTitle = new String[3];
    int[] unreadData ={0, 0, 0};
    Map<String, Long> unreadCount = new HashMap<>();

    private int raffles_point = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        userStatusRef = References.getInstance().usersRef.child(AppManager.getInstance().userId).child("userStatus");
        userStatusRef.onDisconnect().setValue(UserStatus.OFFLINE.ordinal());

        raffles_point = AppManager.getSession().getRaffle_point();

        //Initializing viewPager
        viewPager = (ViewPager) findViewById(R.id.viewpager);
        viewPager.setOffscreenPageLimit(3);
        setupViewPager(viewPager);

        //Initializing the tablayout
        tabLayout = (TabLayout) findViewById(R.id.tablayout);
        tabLayout.setupWithViewPager(viewPager);

        try{
            setupTabIcons();
        }
        catch (Exception e)
        {
            FirebaseCrash.report(e);
        }

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                viewPager.setCurrentItem(position,false);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        viewPager.setCurrentItem(0);

        AppManager.getInstance().setNewsValueListenerMain(this);

        AppManager.getInstance().setUserValueListenerMain(this);

        // push notification part
        OneSignal.startInit(this)
                .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
                .unsubscribeWhenNotificationsAreDisabled(false)
                .setNotificationOpenedHandler(new OneSignal.NotificationOpenedHandler() {
                    @Override
                    public void notificationOpened(OSNotificationOpenResult result) {

                    }
                })
                .setNotificationReceivedHandler(new OneSignal.NotificationReceivedHandler() {
                    @Override
                    public void notificationReceived(OSNotification notification) {
                        Log.d("Notification", notification.toString());
                    }
                })
                .init();

        OneSignal.addPermissionObserver(new OSPermissionObserver() {
            @Override
            public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
                if (stateChanges.getFrom().getEnabled() &&
                        !stateChanges.getTo().getEnabled()) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("Notifications Disabled!")
                            .show();
                }
                Log.i("Debug", "onOSPermissionChanged: " + stateChanges);
            }
        });

        OneSignal.setSubscription(true);
        OneSignal.idsAvailable(new OneSignal.IdsAvailableHandler() {
            @Override
            public void idsAvailable(String userId, String registrationId) {
                Log.d("OneSignal", "PlayerID: " + userId + "\nPushToken: " + registrationId);
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                Map<String, Object> pushToken = new HashMap<>();
                pushToken.put("pushToken", userId);
                database.getReference("Users").child(AppManager.getInstance().userId).updateChildren(pushToken);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        userStatusRef.setValue(UserStatus.ONLINE.ordinal());

        // Clear all notification
        OneSignal.clearOneSignalNotifications();
    }

    @Override
    protected void onPause() {
        super.onPause();

        userStatusRef.setValue(UserStatus.OFFLINE.ordinal());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        userStatusRef.setValue(UserStatus.OFFLINE.ordinal());
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        // Associate searchable configuration with the SearchView

        menuItemRefresh = menu.findItem(R.id.menu_refresh);
        menuItemRefresh.setVisible(false);

        menuItemNews = menu.findItem(R.id.menu_notification);
        MenuItemBadge.update(this, menuItemNews, new MenuItemBadge.Builder()
            .iconDrawable(ContextCompat.getDrawable(this, R.drawable.ic_notification_md))
            .iconTintColor(Color.WHITE)
            .textBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
            .textColor(ContextCompat.getColor(this, R.color.colorPrimary)));
        updateNewsBadgeCount(AppManager.getInstance().newsList);

        menuItemPoints = menu.findItem(R.id.menu_points);
        MenuItemBadge.update(this, menuItemPoints, new MenuItemBadge.Builder()
                .iconDrawable(ContextCompat.getDrawable(this, R.drawable.ic_sack_md))
                .iconTintColor(Color.WHITE)
                .textBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
                .textColor(ContextCompat.getColor(this, R.color.colorPrimary)));
        MenuItemBadge.getBadgeTextView(menuItemPoints).setText(String.valueOf(raffles_point));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_points:
                startActivity(new Intent(this, WalletActivity.class));
                return true;
            case R.id.menu_notification:
                startActivity(new Intent(this, NewsActivity.class));
                return true;
            case R.id.menu_settings:
                // TODO: 9/14/2017 Settings
                Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSelectedChat(Chat chat) {
        if (chat.getUserId() == null ){
            return;
        }

        AppManager.getInstance().selectedChat = chat;
        Intent intent = new Intent(this, ChatActivity.class);
        startActivity(intent);
    }

    @Override
    public void onUpdatedNewsList(List<News> newsList) {
        updateNewsBadgeCount(newsList);
    }

    @Override
    public void onUnreadMessages(String chatId, long count) {
        unreadCount.put(chatId, count);
        int total_unread_count = 0;
        for (Map.Entry<String, Long> entry : unreadCount.entrySet()){
            String key = entry.getKey();
            long value = entry.getValue();
            total_unread_count += value;
        }
        updateTabBadgeCount(0, total_unread_count);
    }

    @Override
    public void onLoadedUser(User user) {
        if (user != null) {
            raffles_point = AppManager.getSession().getRaffle_point();
            if (menuItemPoints != null)
                MenuItemBadge.getBadgeTextView(menuItemPoints).setText(String.valueOf(raffles_point));
        }
    }

    private void updateNewsBadgeCount(List<News> newsList){
        int numberOfnews = 0;
        for (News news : newsList){
            if (!news.isRead()) {
                numberOfnews += 1;
            }
        }
        if (menuItemNews != null)
            MenuItemBadge.getBadgeTextView(menuItemNews).setBadgeCount(numberOfnews);
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        chatFragment = new ChatListFragment();
        chatFragment.setUnreadMessageListener(this);
        chatFragment.setChatItemClickListener(this);
        rafflesFragment = new RafflesFragment();
        contactsFragment = new ContactsFragment();
        contactsFragment.setListener(this);
        adapter.addFragment(chatFragment,"CHAT");
        adapter.addFragment(rafflesFragment, "RAFFLES");
        adapter.addFragment(contactsFragment,"CONTACTS");
        viewPager.setAdapter(adapter);
    }

    private View prepareTabView(int pos) {
        View view = getLayoutInflater().inflate(R.layout.custom_tab,null);
        TextView tv_title = (TextView) view.findViewById(R.id.tv_title);
        TextView tv_count = (TextView) view.findViewById(R.id.tv_count);
        tv_title.setText(tabTitle[pos]);
        if(unreadData[pos] > 0)
        {
            tv_count.setVisibility(View.VISIBLE);
            tv_count.setText(""+ unreadData[pos]);
        }
        else
            tv_count.setVisibility(View.GONE);

        return view;
    }

    private void setupTabIcons()
    {
        tabTitle[0] = getString(R.string.tab_title_chats);
        tabTitle[1] = getString(R.string.tab_title_raffles);
        tabTitle[2] = getString(R.string.tab_title_contacts);

        for(int i = 0; i < tabTitle.length; i++)
        {
            /*TabLayout.Tab tabitem = tabLayout.newTab();
            tabitem.setCustomView(prepareTabView(i));
            tabLayout.addTab(tabitem);*/

            tabLayout.getTabAt(i).setCustomView(prepareTabView(i));
        }

    }

    private void updateTabBadgeCount(int index, int count){
        View customView = tabLayout.getTabAt(index).getCustomView();
        TextView tv_count = (TextView) customView.findViewById(R.id.tv_count);
        if(count > 0)
        {
            tv_count.setVisibility(View.VISIBLE);
            tv_count.setText(""+count);
        }
        else
            tv_count.setVisibility(View.GONE);
    }
}
