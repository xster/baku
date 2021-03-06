// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package examples.baku.io.permissions.examples;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.collect.Iterables;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.joanzapata.iconify.IconDrawable;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.MaterialIcons;
import com.joanzapata.iconify.widget.IconTextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import examples.baku.io.permissions.Blessing;
import examples.baku.io.permissions.PermissionManager;
import examples.baku.io.permissions.PermissionRequest;
import examples.baku.io.permissions.PermissionService;
import examples.baku.io.permissions.R;
import examples.baku.io.permissions.discovery.DevicePickerActivity;

public class EmailActivity extends AppCompatActivity implements ServiceConnection {


    private static final String TAG = PermissionService.class.getSimpleName();
    private DrawerLayout mDrawerLayout;
    private ItemTouchHelper mItemTouchHelper;

    static void l(String msg) {
        Log.e(TAG, msg);
    }

    public static final String KEY_DOCUMENTS = "documents";
    public static final String KEY_EMAILS = "emails";
    public static final String KEY_MESSAGES = "messages";

    private PermissionService mPermissionService;
    private PermissionManager mPermissionManager;
    private String mDeviceId;
    private FirebaseDatabase mFirebaseDB;
    private DatabaseReference mMessagesRef;

    private Toolbar mToolbar;

    private RecyclerView mInboxRecyclerView;
    private MessagesAdapter mInboxAdapter;
    private LinearLayoutManager mLayoutManager;

    private LinkedHashMap<String, MessageData> mMessages = new LinkedHashMap<>();

    private ArrayList<String> mMessageOrder = new ArrayList<>();

    private ListView mDrawerList;
    private String[] mGroupList = new String[]{"Inbox", "Sent", "Drafts"};
    private String mGroup = "Inbox";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitle(mGroup);
        setSupportActionBar(mToolbar);
        mToolbar.setNavigationIcon(
                new IconDrawable(this, MaterialIcons.md_menu)
                        .color(Color.WHITE)
                        .actionBarSize());
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDrawerLayout.openDrawer(mDrawerList);
                mDrawerLayout.openDrawer(mDrawerList);
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ComposeActivity.launchAndCreateMessage(EmailActivity.this, mMessagesRef, "", "myself@email.com", "", "", null, "Drafts");

                }
            });
        }

        PermissionService.start(this);
        PermissionService.bind(this);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);
        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.navigation_menu_item, R.id.navigationItemText, mGroupList));
        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mDrawerList.setSelection(position);
                mDrawerLayout.closeDrawers();
                setGroup(mGroupList[position]);
            }
        });
        mDrawerList.setSelection(0);


        mInboxAdapter = new MessagesAdapter(mMessages);
        mLayoutManager = new LinearLayoutManager(this);
        mInboxRecyclerView = (RecyclerView) findViewById(R.id.inboxRecyclerView);
        mInboxRecyclerView.setLayoutManager(mLayoutManager);
        mInboxRecyclerView.setAdapter(mInboxAdapter);

        //add swipe behavior
        mItemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
//        itemTouchHelper.attachToRecyclerView(mInboxRecyclerView);

        setGroup(mGroup);
    }

    public void setGroup(String group) {
        mGroup = group;
        mToolbar.setTitle(group);
        mInboxAdapter.setGroup(group);
        if ("Sent".equals(group)) {
            mItemTouchHelper.attachToRecyclerView(mInboxRecyclerView);
        } else {
            mItemTouchHelper.attachToRecyclerView(null);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_permission, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_cast) {

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    MessageData addNewMessage(String group) {
        MessageData msg = new MessageData(UUID.randomUUID().toString(), "", "", "", "", null, group);
        mMessagesRef.child(msg.getId()).setValue(msg);
        return msg;
    }

    ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
            //Remove swiped item from list and notify the RecyclerView
            int pos = viewHolder.getAdapterPosition();
            MessageData item = mInboxAdapter.getItem(pos);
            //TOOO: bug, item id shouldn't ever be null
            if (item != null && item.getId() != null) {
                mMessagesRef.child(item.getId()).removeValue();
            }
        }
    };

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        PermissionService.PermissionServiceBinder binder = (PermissionService.PermissionServiceBinder) service;
        mPermissionService = binder.getInstance();
        if (mPermissionService != null) {
            mDeviceId = mPermissionService.getDeviceId();
            mPermissionManager = mPermissionService.getPermissionManager();
            mFirebaseDB = mPermissionService.getFirebaseDB();
            mMessagesRef = mFirebaseDB.getReference(KEY_DOCUMENTS).child(mDeviceId).child(KEY_EMAILS).child(KEY_MESSAGES);
            mMessagesRef.addValueEventListener(messagesValueListener);
            mMessagesRef.addChildEventListener(messageChildListener);

            mPermissionManager.addOnRequestListener("documents/" + mDeviceId + "/emails/messages/*", new PermissionManager.OnRequestListener() {
                @Override
                public boolean onRequest(PermissionRequest request, Blessing blessing) {
                    mInboxAdapter.refreshDataSet();
                    return true;
                }

                @Override
                public void onRequestRemoved(PermissionRequest request, Blessing blessing) {
                    mInboxAdapter.notifyDataSetChanged();
                }
            });

            //TEMP: example status
            mPermissionService.clearStatus(ComposeActivity.EXTRA_MESSAGE_PATH);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }


    private ChildEventListener messageChildListener = new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            onMessageUpdated(dataSnapshot);
            mInboxAdapter.refreshDataSet();
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {
            onMessageUpdated(dataSnapshot);
            mInboxAdapter.refreshDataSet();
        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {
            onMessageRemoved(dataSnapshot.getKey());
            mInboxAdapter.refreshDataSet();
        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
    };

    private ValueEventListener messagesValueListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            onMessagesUpdated(dataSnapshot);
            mInboxAdapter.refreshDataSet();
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
    };

    void onMessagesUpdated(DataSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("null snapshot");

        mMessages.clear();
        for (DataSnapshot snap : snapshot.getChildren()) {
            onMessageUpdated(snap);
        }
    }

    void onMessageUpdated(DataSnapshot snapshot) {
        try {
            MessageData msg = snapshot.getValue(MessageData.class);
            String key = msg.getId();
            mMessages.put(key, msg);
        } catch (DatabaseException e) {
            e.printStackTrace();
        }
    }

    void onMessageRemoved(String id) {
        mMessages.remove(id);
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {
        public CardView mCardView;

        public ViewHolder(CardView v) {
            super(v);
            mCardView = v;

            mCardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DevicePickerActivity.REQUEST_DEVICE_ID && data != null && data.hasExtra(DevicePickerActivity.EXTRA_DEVICE_ID)) {
            String targetDevice = data.getStringExtra(DevicePickerActivity.EXTRA_DEVICE_ID);
            String path = data.getStringExtra(DevicePickerActivity.EXTRA_REQUEST_ARGS);


            if (!mDeviceId.equals(targetDevice)) {
                mPermissionManager.bless(targetDevice)
                        .setPermissions(path + "/to", PermissionManager.FLAG_READ)
                        .setPermissions(path + "/message", PermissionManager.FLAG_SUGGEST)
                        .setPermissions(path + "/subject", PermissionManager.FLAG_SUGGEST);
            }
            JSONObject castArgs = new JSONObject();
            try {
                castArgs.put("activity", ComposeActivity.class.getSimpleName());
                castArgs.put(ComposeActivity.EXTRA_MESSAGE_PATH, path);
                mPermissionService.updateConstellationDevice(targetDevice);
                mPermissionService.getMessenger().to(targetDevice).emit("cast", castArgs.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public class MessagesAdapter extends RecyclerView.Adapter<ViewHolder> {

        private LinkedHashMap<String, MessageData> mRawDataSet;
        private LinkedHashMap<String, MessageData> mDataSet = new LinkedHashMap<>();

        private String mGroup;

        public MessagesAdapter(LinkedHashMap<String, MessageData> dataset) {
            setDataset(dataset);
        }

        public void setDataset(LinkedHashMap<String, MessageData> mDataset) {
            this.mRawDataSet = mDataset;
            refreshDataSet();
        }

        public void refreshDataSet() {
            mDataSet.clear();
            for (Map.Entry<String, MessageData> entry : mRawDataSet.entrySet()) {
                if (mGroup == null || entry.getValue().getGroup().equals(mGroup)) {
                    mDataSet.put(entry.getKey(), entry.getValue());
                }
            }
            notifyDataSetChanged();
        }

        public MessageData getItem(int position) {
            return Iterables.get(mDataSet.values(), position);
        }

        public void setGroup(String group) {
            this.mGroup = group;
            refreshDataSet();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent,
                                             int viewType) {
            // create a new view
            CardView v = (CardView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.inbox_card_item, parent, false);
            // set the view's size, margins, paddings and layout parameters
            ViewHolder vh = new ViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {

            final MessageData item = getItem(position);

            String title = "";
            if ("Inbox".equals(mGroup)) {
                title = item.getFrom();
            } else {
                title = item.getTo();
            }
            String subtitle = item.getSubject();
            TextView titleView = (TextView) holder.mCardView.findViewById(R.id.card_title);
            titleView.setText(title);
            TextView subtitleView = (TextView) holder.mCardView.findViewById(R.id.card_subtitle);
            subtitleView.setText(subtitle);

            IconTextView castButton = (IconTextView) holder.mCardView.findViewById(R.id.card_trailing);
            castButton.setVisibility(View.GONE);
//            castButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    //choose device
//                    Intent requestIntent = new Intent(EmailActivity.this, DevicePickerActivity.class);
//                    String path = EmailActivity.KEY_DOCUMENTS
//                            + "/" + mDeviceId
//                            + "/" + EmailActivity.KEY_EMAILS
//                            + "/" + EmailActivity.KEY_MESSAGES
//                            + "/" + item.getId();
//                    requestIntent.putExtra(DevicePickerActivity.EXTRA_REQUEST, DevicePickerActivity.REQUEST_DEVICE_ID);
//                    requestIntent.putExtra(DevicePickerActivity.EXTRA_REQUEST_ARGS, path);
//                    startActivityForResult(requestIntent, DevicePickerActivity.REQUEST_DEVICE_ID);
//                }
//            });

            holder.mCardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(EmailActivity.this, ComposeActivity.class);
                    intent.putExtra(ComposeActivity.EXTRA_MESSAGE_ID, item.getId());
                    startActivityForResult(intent, 0);
                }
            });

            holder.mCardView.setCardBackgroundColor(Color.WHITE);
            if (mPermissionManager != null) {
                String path = "documents/" + mDeviceId + "/emails/messages/" + item.getId() + "/*";
                for (PermissionRequest request : mPermissionManager.getRequests(path)) {
                    holder.mCardView.setCardBackgroundColor(Color.GRAY);
                    break;
                }

            }

        }

        @Override
        public int getItemCount() {
            return mDataSet.size();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mPermissionService != null) {
            //TEMP: example status
            mPermissionService.clearStatus(ComposeActivity.EXTRA_MESSAGE_PATH);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }


}