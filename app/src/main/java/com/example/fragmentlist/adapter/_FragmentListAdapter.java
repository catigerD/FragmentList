/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.fragmentlist.adapter;

import static androidx.lifecycle.Lifecycle.State.RESUMED;
import static androidx.lifecycle.Lifecycle.State.STARTED;

import static com.example.fragmentlist.adapter.Preconditions.checkArgument;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.RequiresOptIn;
import androidx.collection.ArraySet;
import androidx.collection.LongSparseArray;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Similar in behavior to {@link androidx.fragment.app.FragmentStatePagerAdapter
 * FragmentStatePagerAdapter}
 * <p>
 * Lifecycle within {@link RecyclerView}:
 * <ul>
 * <li>{@link RecyclerView.ViewHolder} initially an empty {@link FrameLayout}, serves as a
 * re-usable container for a {@link Fragment} in later stages.
 * <li>{@link RecyclerView.Adapter#onBindViewHolder} we ask for a {@link Fragment} for the
 * position. If we already have the fragment, or have previously saved its state, we use those.
 * <li>{@link RecyclerView.Adapter#onAttachedToWindow} we attach the {@link Fragment} to a
 * container.
 * <li>{@link RecyclerView.Adapter#onViewRecycled} we remove, save state, destroy the
 * {@link Fragment}.
 * </ul>
 */
public abstract class _FragmentListAdapter extends
        RecyclerView.Adapter<FragmentListViewHolder> implements StatefulAdapter {
    // State saving config
    private static final String KEY_PREFIX_FRAGMENT = "f#";
    private static final String KEY_PREFIX_STATE = "s#";

    // Fragment GC config
    private static final long GRACE_WINDOW_TIME_MS = 10_000; // 10 seconds

    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    final Lifecycle mLifecycle;
    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    final FragmentManager mFragmentManager;

    // Fragment bookkeeping
    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    final LongSparseArray<Fragment> mFragments = new LongSparseArray<>();
    private final LongSparseArray<Fragment.SavedState> mSavedStates = new LongSparseArray<>();
    private final LongSparseArray<Integer> mItemIdToViewHolder = new LongSparseArray<>();

    private FragmentMaxLifecycleEnforcer mFragmentMaxLifecycleEnforcer;

    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
            FragmentEventDispatcher mFragmentEventDispatcher = new FragmentEventDispatcher();

    // Fragment GC
    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    boolean mIsInGracePeriod = false;
    private boolean mHasStaleFragments = false;

    @RecyclerView.Orientation
    private int orientation = RecyclerView.VERTICAL;

    /**
     * @param fragmentActivity if the {@link ViewPager2} lives directly in a
     * {@link FragmentActivity} subclass.
     *
     * @see _FragmentListAdapter#_FragmentListAdapter(Fragment)
     * @see _FragmentListAdapter#_FragmentListAdapter(FragmentManager, Lifecycle)
     */
    public _FragmentListAdapter(@NonNull FragmentActivity fragmentActivity) {
        this(fragmentActivity.getSupportFragmentManager(), fragmentActivity.getLifecycle());
    }

    /**
     * @param fragment if the {@link ViewPager2} lives directly in a {@link Fragment} subclass.
     *
     * @see _FragmentListAdapter#_FragmentListAdapter(FragmentActivity)
     * @see _FragmentListAdapter#_FragmentListAdapter(FragmentManager, Lifecycle)
     */
    public _FragmentListAdapter(@NonNull Fragment fragment) {
        this(fragment.getChildFragmentManager(), fragment.getLifecycle());
    }

    /**
     * @param fragmentManager of {@link ViewPager2}'s host
     * @param lifecycle of {@link ViewPager2}'s host
     *
     * @see _FragmentListAdapter#_FragmentListAdapter(FragmentActivity)
     * @see _FragmentListAdapter#_FragmentListAdapter(Fragment)
     */
    public _FragmentListAdapter(@NonNull FragmentManager fragmentManager,
                                @NonNull Lifecycle lifecycle) {
        mFragmentManager = fragmentManager;
        mLifecycle = lifecycle;
        super.setHasStableIds(true);
    }

    @CallSuper
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        checkArgument(mFragmentMaxLifecycleEnforcer == null);
        mFragmentMaxLifecycleEnforcer = new FragmentMaxLifecycleEnforcer();
        mFragmentMaxLifecycleEnforcer.register(recyclerView);
        orientation = RecyclerViewExt.orientation(recyclerView);
    }

    @CallSuper
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        mFragmentMaxLifecycleEnforcer.unregister(recyclerView);
        mFragmentMaxLifecycleEnforcer = null;
    }

    /**
     * Provide a new Fragment associated with the specified position.
     * <p>
     * The adapter will be responsible for the Fragment lifecycle:
     * <ul>
     *     <li>The Fragment will be used to display an item.</li>
     *     <li>The Fragment will be destroyed when it gets too far from the viewport, and its state
     *     will be saved. When the item is close to the viewport again, a new Fragment will be
     *     requested, and a previously saved state will be used to initialize it.
     * </ul>
     * @see ViewPager2#setOffscreenPageLimit
     */
    public abstract @NonNull Fragment createFragment(int position);

    @NonNull
    @Override
    public final FragmentListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return FragmentListViewHolder.create(parent, orientation);
    }

    @Override
    public final void onBindViewHolder(final @NonNull FragmentListViewHolder holder, int position) {
        final long itemId = holder.getItemId();
        final int viewHolderId = holder.getContainer().getId();
        final Long boundItemId = itemForViewHolder(viewHolderId); // item currently bound to the VH
        if (boundItemId != null && boundItemId != itemId) {
            removeFragment(boundItemId);
            mItemIdToViewHolder.remove(boundItemId);
        }

        mItemIdToViewHolder.put(itemId, viewHolderId); // this might overwrite an existing entry
        ensureFragment(position);

        /* Special case when {@link RecyclerView} decides to keep the {@link container}
         * attached to the window, resulting in no {@link `onViewAttachedToWindow} callback later */
        final FrameLayout container = holder.getContainer();
        if (container.isAttachedToWindow()) {
            placeFragmentInViewHolder(holder);
        }

        gcFragments();
    }

    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    void gcFragments() {
        if (!mHasStaleFragments || shouldDelayFragmentTransactions()) {
            return;
        }

        // Remove Fragments for items that are no longer part of the data-set
        Set<Long> toRemove = new ArraySet<>();
        for (int ix = 0; ix < mFragments.size(); ix++) {
            long itemId = mFragments.keyAt(ix);
            if (!containsItem(itemId)) {
                toRemove.add(itemId);
                mItemIdToViewHolder.remove(itemId); // in case they're still bound
            }
        }

        // Remove Fragments that are not bound anywhere -- pending a grace period
        if (!mIsInGracePeriod) {
            mHasStaleFragments = false; // we've executed all GC checks

            for (int ix = 0; ix < mFragments.size(); ix++) {
                long itemId = mFragments.keyAt(ix);
                if (!isFragmentViewBound(itemId)) {
                    toRemove.add(itemId);
                }
            }
        }

        for (Long itemId : toRemove) {
            removeFragment(itemId);
        }
    }

    private boolean isFragmentViewBound(long itemId) {
        if (mItemIdToViewHolder.containsKey(itemId)) {
            return true;
        }

        Fragment fragment = mFragments.get(itemId);
        if (fragment == null) {
            return false;
        }

        View view = fragment.getView();
        if (view == null) {
            return false;
        }

        return view.getParent() != null;
    }

    private Long itemForViewHolder(int viewHolderId) {
        Long boundItemId = null;
        for (int ix = 0; ix < mItemIdToViewHolder.size(); ix++) {
            if (mItemIdToViewHolder.valueAt(ix) == viewHolderId) {
                if (boundItemId != null) {
                    throw new IllegalStateException("Design assumption violated: "
                            + "a ViewHolder can only be bound to one item at a time.");
                }
                boundItemId = mItemIdToViewHolder.keyAt(ix);
            }
        }
        return boundItemId;
    }

    private void ensureFragment(int position) {
        long itemId = getItemId(position);
        if (!mFragments.containsKey(itemId)) {
            // TODO(133419201): check if a Fragment provided here is a new Fragment
            Fragment newFragment = createFragment(position);
            newFragment.setInitialSavedState(mSavedStates.get(itemId));
            mFragments.put(itemId, newFragment);
        }
    }

    @Override
    public final void onViewAttachedToWindow(@NonNull final FragmentListViewHolder holder) {
        placeFragmentInViewHolder(holder);
        gcFragments();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull FragmentListViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        mFragmentMaxLifecycleEnforcer.updateFragmentMaxLifecycle(false);
    }

    /**
     * @param holder that has been bound to a Fragment in the {@link #onBindViewHolder} stage.
     */
    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    void placeFragmentInViewHolder(@NonNull final FragmentListViewHolder holder) {
        Fragment fragment = mFragments.get(holder.getItemId());
        if (fragment == null) {
            throw new IllegalStateException("Design assumption violated.");
        }
        FrameLayout container = holder.getContainer();
        View view = fragment.getView();

        /*
        possible states:
        - fragment: { added, notAdded }
        - view: { created, notCreated }
        - view: { attached, notAttached }

        combinations:
        - { f:added, v:created, v:attached } -> check if attached to the right container
        - { f:added, v:created, v:notAttached} -> attach view to container
        - { f:added, v:notCreated, v:attached } -> impossible
        - { f:added, v:notCreated, v:notAttached} -> schedule callback for when created
        - { f:notAdded, v:created, v:attached } -> illegal state
        - { f:notAdded, v:created, v:notAttached } -> illegal state
        - { f:notAdded, v:notCreated, v:attached } -> impossible
        - { f:notAdded, v:notCreated, v:notAttached } -> add, create, attach
         */

        // { f:notAdded, v:created, v:attached } -> illegal state
        // { f:notAdded, v:created, v:notAttached } -> illegal state
        if (!fragment.isAdded() && view != null) {
            throw new IllegalStateException("Design assumption violated.");
        }

        // { f:added, v:notCreated, v:notAttached} -> schedule callback for when created
        if (fragment.isAdded() && view == null) {
            scheduleViewAttach(fragment, container);
            return;
        }

        // { f:added, v:created, v:attached } -> check if attached to the right container
        if (fragment.isAdded() && view.getParent() != null) {
            if (view.getParent() != container) {
                addViewToContainer(view, container);
            }
            return;
        }

        // { f:added, v:created, v:notAttached} -> attach view to container
        if (fragment.isAdded()) {
            addViewToContainer(view, container);
            return;
        }

        // { f:notAdded, v:notCreated, v:notAttached } -> add, create, attach
        if (!shouldDelayFragmentTransactions()) {
            scheduleViewAttach(fragment, container);
            List<FragmentTransactionCallback.OnPostEventListener> onPost =
                    mFragmentEventDispatcher.dispatchPreAdded(fragment);
            try {
                fragment.setMenuVisibility(false); // appropriate for maxLifecycle == STARTED
                mFragmentManager.beginTransaction()
                        .add(fragment, "f" + holder.getItemId())
                        .setMaxLifecycle(fragment, STARTED)
                        .commitNow();
                mFragmentMaxLifecycleEnforcer.updateFragmentMaxLifecycle(false);
            } finally {
                mFragmentEventDispatcher.dispatchPostEvents(onPost);
            }
        } else {
            if (mFragmentManager.isDestroyed()) {
                return; // nothing we can do
            }
            mLifecycle.addObserver(new LifecycleEventObserver() {
                @Override
                public void onStateChanged(@NonNull LifecycleOwner source,
                        @NonNull Lifecycle.Event event) {
                    if (shouldDelayFragmentTransactions()) {
                        return;
                    }
                    source.getLifecycle().removeObserver(this);
                    if (holder.getContainer().isAttachedToWindow()) {
                        placeFragmentInViewHolder(holder);
                    }
                }
            });
        }
    }

    private void scheduleViewAttach(final Fragment fragment, @NonNull final FrameLayout container) {
        // After a config change, Fragments that were in FragmentManager will be recreated. Since
        // ViewHolder container ids are dynamically generated, we opted to manually handle
        // attaching Fragment views to containers. For consistency, we use the same mechanism for
        // all Fragment views.
        mFragmentManager.registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    // TODO(b/141956012): Suppressed during upgrade to AGP 3.6.
                    @SuppressWarnings("ReferenceEquality")
                    @Override
                    public void onFragmentViewCreated(@NonNull FragmentManager fm,
                            @NonNull Fragment f, @NonNull View v,
                            @Nullable Bundle savedInstanceState) {
                        if (f == fragment) {
                            fm.unregisterFragmentLifecycleCallbacks(this);
                            addViewToContainer(v, container);
                        }
                    }
                }, false);
    }

    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    void addViewToContainer(@NonNull View v, @NonNull FrameLayout container) {
        if (container.getChildCount() > 1) {
            throw new IllegalStateException("Design assumption violated.");
        }

        if (v.getParent() == container) {
            return;
        }

        if (container.getChildCount() > 0) {
            container.removeAllViews();
        }

        if (v.getParent() != null) {
            ((ViewGroup) v.getParent()).removeView(v);
        }

        container.addView(v);
    }

    @Override
    public final void onViewRecycled(@NonNull FragmentListViewHolder holder) {
        final int viewHolderId = holder.getContainer().getId();
        final Long boundItemId = itemForViewHolder(viewHolderId); // item currently bound to the VH
        if (boundItemId != null) {
            removeFragment(boundItemId);
            mItemIdToViewHolder.remove(boundItemId);
        }
    }

    @Override
    public final boolean onFailedToRecycleView(@NonNull FragmentListViewHolder holder) {
        /*
         This happens when a ViewHolder is in a transient state (e.g. during an
         animation).

         Our ViewHolders are effectively just FrameLayout instances in which we put Fragment
         Views, so it's safe to force recycle them. This is because:
         - FrameLayout instances are not to be directly manipulated, so no animations are
         expected to be running directly on them.
         - Fragment Views are not reused between position (one Fragment = one page). Animation
         running in one of the Fragment Views won't affect another Fragment View.
         - If a user chooses to violate these assumptions, they are also in the position to
         correct the state in their code.
        */
        return true;
    }

    private void removeFragment(long itemId) {
        Fragment fragment = mFragments.get(itemId);

        if (fragment == null) {
            return;
        }

        if (fragment.getView() != null) {
            ViewParent viewParent = fragment.getView().getParent();
            if (viewParent != null) {
                ((FrameLayout) viewParent).removeAllViews();
            }
        }

        if (!containsItem(itemId)) {
            mSavedStates.remove(itemId);
        }

        if (!fragment.isAdded()) {
            mFragments.remove(itemId);
            return;
        }

        if (shouldDelayFragmentTransactions()) {
            mHasStaleFragments = true;
            return;
        }

        if (fragment.isAdded() && containsItem(itemId)) {
            List<FragmentTransactionCallback.OnPostEventListener> onPost =
                    mFragmentEventDispatcher.dispatchPreSavedInstanceState(fragment);
            Fragment.SavedState savedState = mFragmentManager.saveFragmentInstanceState(fragment);
            mFragmentEventDispatcher.dispatchPostEvents(onPost);

            mSavedStates.put(itemId, savedState);
        }
        List<FragmentTransactionCallback.OnPostEventListener> onPost =
                mFragmentEventDispatcher.dispatchPreRemoved(fragment);
        try {
            mFragmentManager.beginTransaction().remove(fragment).commitNow();
            mFragments.remove(itemId);
        } finally {
            mFragmentEventDispatcher.dispatchPostEvents(onPost);
        }
    }

    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    boolean shouldDelayFragmentTransactions() {
        return mFragmentManager.isStateSaved();
    }

    /**
     * Default implementation works for collections that don't add, move, remove items.
     * <p>
     * When overriding, also override {@link #containsItem(long)}.
     * <p>
     * If the item is not a part of the collection, return {@link RecyclerView#NO_ID}.
     *
     * @param position Adapter position
     * @return stable item id {@link RecyclerView.Adapter#hasStableIds()}
     */
    // TODO(b/122670460): add lint rule
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Default implementation works for collections that don't add, move, remove items.
     * <p>
     * When overriding, also override {@link #getItemId(int)}
     */
    // TODO(b/122670460): add lint rule
    public boolean containsItem(long itemId) {
        return itemId >= 0 && itemId < getItemCount();
    }

    @Override
    public final void setHasStableIds(boolean hasStableIds) {
        throw new UnsupportedOperationException(
                "Stable Ids are required for the adapter to function properly, and the adapter "
                        + "takes care of setting the flag.");
    }

    @Override
    public final @NonNull Parcelable saveState() {
        /* TODO(b/122670461): use custom {@link Parcelable} instead of Bundle to save space */
        Bundle savedState = new Bundle(mFragments.size() + mSavedStates.size());

        /* save references to active fragments */
        for (int ix = 0; ix < mFragments.size(); ix++) {
            long itemId = mFragments.keyAt(ix);
            Fragment fragment = mFragments.get(itemId);
            if (fragment != null && fragment.isAdded()) {
                String key = createKey(KEY_PREFIX_FRAGMENT, itemId);
                mFragmentManager.putFragment(savedState, key, fragment);
            }
        }

        /* Write {@link mSavedStates) into a {@link Parcelable} */
        for (int ix = 0; ix < mSavedStates.size(); ix++) {
            long itemId = mSavedStates.keyAt(ix);
            if (containsItem(itemId)) {
                String key = createKey(KEY_PREFIX_STATE, itemId);
                savedState.putParcelable(key, mSavedStates.get(itemId));
            }
        }

        return savedState;
    }

    @Override
    @SuppressWarnings("deprecation")
    public final void restoreState(@NonNull Parcelable savedState) {
        if (!mSavedStates.isEmpty() || !mFragments.isEmpty()) {
            throw new IllegalStateException(
                    "Expected the adapter to be 'fresh' while restoring state.");
        }

        Bundle bundle = (Bundle) savedState;
        if (bundle.getClassLoader() == null) {
            /* TODO(b/133752041): pass the class loader from {@link ViewPager2.SavedState } */
            bundle.setClassLoader(getClass().getClassLoader());
        }

        for (String key : bundle.keySet()) {
            if (isValidKey(key, KEY_PREFIX_FRAGMENT)) {
                long itemId = parseIdFromKey(key, KEY_PREFIX_FRAGMENT);
                Fragment fragment = mFragmentManager.getFragment(bundle, key);
                mFragments.put(itemId, fragment);
                continue;
            }

            if (isValidKey(key, KEY_PREFIX_STATE)) {
                long itemId = parseIdFromKey(key, KEY_PREFIX_STATE);
                Fragment.SavedState state = bundle.getParcelable(key);
                if (containsItem(itemId)) {
                    mSavedStates.put(itemId, state);
                }
                continue;
            }

            throw new IllegalArgumentException("Unexpected key in savedState: " + key);
        }

        if (!mFragments.isEmpty()) {
            mHasStaleFragments = true;
            mIsInGracePeriod = true;
            gcFragments();
            scheduleGracePeriodEnd();
        }
    }

    private void scheduleGracePeriodEnd() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mIsInGracePeriod = false;
                gcFragments(); // good opportunity to GC
            }
        };

        mLifecycle.addObserver(new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner source,
                    @NonNull Lifecycle.Event event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    handler.removeCallbacks(runnable);
                    source.getLifecycle().removeObserver(this);
                }
            }
        });

        handler.postDelayed(runnable, GRACE_WINDOW_TIME_MS);
    }

    // Helper function for dealing with save / restore state
    private static @NonNull String createKey(@NonNull String prefix, long id) {
        return prefix + id;
    }

    // Helper function for dealing with save / restore state
    private static boolean isValidKey(@NonNull String key, @NonNull String prefix) {
        return key.startsWith(prefix) && key.length() > prefix.length();
    }

    // Helper function for dealing with save / restore state
    private static long parseIdFromKey(@NonNull String key, @NonNull String prefix) {
        return Long.parseLong(key.substring(prefix.length()));
    }

    /**
     * Pauses (STARTED) all Fragments that are attached and not a primary item.
     * Keeps primary item Fragment RESUMED.
     */
    class FragmentMaxLifecycleEnforcer {
        private RecyclerView.OnScrollListener mScrollListener;
        private RecyclerView.AdapterDataObserver mDataObserver;
        private LifecycleEventObserver mLifecycleObserver;
        private RecyclerView mRecyclerView;

        private List<Long> mVisibleItemIds = new ArrayList<>();

        void register(@NonNull RecyclerView recyclerView) {
            mRecyclerView = recyclerView;

            // signal 1 of 3: current item has changed
            mScrollListener = new FragmentListScrollListener(aBoolean -> {
                updateFragmentMaxLifecycle(false, aBoolean);
                return null;
            });
            mRecyclerView.addOnScrollListener(mScrollListener);

            // signal 2 of 3: underlying data-set has been updated
            mDataObserver = new DataSetChangeObserver() {
                @Override
                public void onChanged() {
                    updateFragmentMaxLifecycle(true);
                }
            };
            registerAdapterDataObserver(mDataObserver);

            // signal 3 of 3: we may have to catch-up after being in a lifecycle state that
            // prevented us to perform transactions
            mLifecycleObserver = new LifecycleEventObserver() {
                @Override
                public void onStateChanged(@NonNull LifecycleOwner source,
                        @NonNull Lifecycle.Event event) {
                    updateFragmentMaxLifecycle(false);
                }
            };
            mLifecycle.addObserver(mLifecycleObserver);
        }

        void unregister(@NonNull RecyclerView recyclerView) {
            mRecyclerView.removeOnScrollListener(mScrollListener);
            unregisterAdapterDataObserver(mDataObserver);
            mLifecycle.removeObserver(mLifecycleObserver);
            mRecyclerView = null;
        }

        void updateFragmentMaxLifecycle(boolean dataSetChanged) {
            updateFragmentMaxLifecycle(dataSetChanged, false);
        }

        void updateFragmentMaxLifecycle(boolean dataSetChanged, boolean ignoreState) {
            if (shouldDelayFragmentTransactions()) {
                return; /* recovery step via {@link #mLifecycleObserver} */
            }

            if (!ignoreState && mRecyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                return; // do not update while not idle to avoid jitter
            }

            if (mFragments.isEmpty() || getItemCount() == 0) {
                return; // nothing to do
            }

            Pair<Integer, Integer> visibleItems = RecyclerViewExt.getVisiblePositions(mRecyclerView);
            if (visibleItems.first >= getItemCount()
                    || visibleItems.first < 0
                    || visibleItems.second >= getItemCount()
                    || visibleItems.second < 0
            ) {
                /* current item is yet to be updated; it is guaranteed to change, so we will be
                 * notified via {@link ViewPager2.OnPageChangeCallback#onPageSelected(int)}  */
                return;
            }

            List<Long> visibleItemIds = new ArrayList<>();
            for (int index = visibleItems.first; index <= visibleItems.second; index++) {
                visibleItemIds.add(getItemId(index));
            }

            if (visibleItemIds.equals(mVisibleItemIds) && !dataSetChanged) {
                return; // nothing to do
            }

            mVisibleItemIds = visibleItemIds;
            FragmentTransaction transaction = mFragmentManager.beginTransaction();

            List<Fragment> toResumeList = new ArrayList<>();
            List<List<FragmentTransactionCallback.OnPostEventListener>> onPost = new ArrayList<>();
            for (int ix = 0; ix < mFragments.size(); ix++) {
                long itemId = mFragments.keyAt(ix);
                Fragment fragment = mFragments.valueAt(ix);

                if (!fragment.isAdded()) {
                    continue;
                }

                if (!mVisibleItemIds.contains(itemId)) {
                    transaction.setMaxLifecycle(fragment, STARTED);
                    onPost.add(mFragmentEventDispatcher.dispatchMaxLifecyclePreUpdated(fragment,
                            STARTED));
                    fragment.setMenuVisibility(false);
                } else {
                    toResumeList.add(fragment); // itemId map key, so only one can match the predicate
                    fragment.setMenuVisibility(true);
                }
            }
            for (Fragment fragment : toResumeList) {
                transaction.setMaxLifecycle(fragment, RESUMED);
                onPost.add(mFragmentEventDispatcher.dispatchMaxLifecyclePreUpdated(fragment,
                        RESUMED));
            }

            if (!transaction.isEmpty()) {
                transaction.commitNow();
                Collections.reverse(onPost); // to assure 'nesting' of events
                for (List<FragmentTransactionCallback.OnPostEventListener> event : onPost) {
                    mFragmentEventDispatcher.dispatchPostEvents(event);
                }
            }
        }
    }

    /**
     * Simplified {@link RecyclerView.AdapterDataObserver} for clients interested in any data-set
     * changes regardless of their nature.
     */
    private abstract static class DataSetChangeObserver extends RecyclerView.AdapterDataObserver {
        @Override
        public abstract void onChanged();

        @Override
        public final void onItemRangeChanged(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public final void onItemRangeChanged(int positionStart, int itemCount,
                @Nullable Object payload) {
            onChanged();
        }

        @Override
        public final void onItemRangeInserted(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public final void onItemRangeRemoved(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public final void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            onChanged();
        }
    }

    @SuppressWarnings("WeakerAccess") // to avoid creation of a synthetic accessor
    static class FragmentEventDispatcher {
        private List<FragmentTransactionCallback> mCallbacks = new CopyOnWriteArrayList<>();

        public void registerCallback(FragmentTransactionCallback callback) {
            mCallbacks.add(callback);
        }

        public void unregisterCallback(FragmentTransactionCallback callback) {
            mCallbacks.remove(callback);
        }

        public List<FragmentTransactionCallback.OnPostEventListener> dispatchMaxLifecyclePreUpdated(Fragment fragment,
                                                                                                    Lifecycle.State maxState) {
            List<FragmentTransactionCallback.OnPostEventListener> result = new ArrayList<>();
            for (FragmentTransactionCallback callback : mCallbacks) {
                result.add(callback.onFragmentMaxLifecyclePreUpdated(fragment, maxState));
            }
            return result;
        }

        public void dispatchPostEvents(List<FragmentTransactionCallback.OnPostEventListener> entries) {
            for (FragmentTransactionCallback.OnPostEventListener entry : entries) {
                entry.onPost();
            }
        }

        public List<FragmentTransactionCallback.OnPostEventListener> dispatchPreAdded(Fragment fragment) {
            List<FragmentTransactionCallback.OnPostEventListener> result = new ArrayList<>();
            for (FragmentTransactionCallback callback : mCallbacks) {
                result.add(callback.onFragmentPreAdded(fragment));
            }
            return result;
        }

        @OptIn(markerClass = ExperimentalFragmentStateAdapterApi.class)
        public List<FragmentTransactionCallback.OnPostEventListener> dispatchPreSavedInstanceState(Fragment fragment) {
            List<FragmentTransactionCallback.OnPostEventListener> result = new ArrayList<>();
            for (FragmentTransactionCallback callback : mCallbacks) {
                result.add(callback.onFragmentPreSavedInstanceState(fragment));
            }
            return result;
        }

        public List<FragmentTransactionCallback.OnPostEventListener> dispatchPreRemoved(Fragment fragment) {
            List<FragmentTransactionCallback.OnPostEventListener> result = new ArrayList<>();
            for (FragmentTransactionCallback callback : mCallbacks) {
                result.add(callback.onFragmentPreRemoved(fragment));
            }
            return result;
        }
    }

    /**
     * Callback interface for listening to fragment lifecycle changes that happen
     * inside the adapter.
     */
    public abstract static class FragmentTransactionCallback {
        private static final @NonNull OnPostEventListener NO_OP = new OnPostEventListener() {
            @Override
            public void onPost() {
                // do nothing
            }
        };

        /**
         * Called right before the Fragment is added to adapter's FragmentManager.
         *
         * @param fragment Fragment changing state
         * @return Listener called after the operation
         */
        @NonNull
        public OnPostEventListener onFragmentPreAdded(@NonNull Fragment fragment) {
            return NO_OP;
        }

        /**
         * Called right before Fragment's state is being saved through a
         * {@link FragmentManager#saveFragmentInstanceState} call.
         *
         * @param fragment Fragment which state is being saved
         * @return Listener called after the operation
         */
        @NonNull
        @ExperimentalFragmentStateAdapterApi // Experimental in v1.1.*. To become stable in v1.2.*.
        public OnPostEventListener onFragmentPreSavedInstanceState(@NonNull Fragment fragment) {
            return NO_OP;
        }

        /**
         * Called right before the Fragment is removed from adapter's FragmentManager.
         *
         * @param fragment Fragment changing state
         * @return Listener called after the operation
         */
        @NonNull
        public OnPostEventListener onFragmentPreRemoved(@NonNull Fragment fragment) {
            return NO_OP;
        }

        /**
         * Called right before Fragment's maximum state is capped via
         * {@link FragmentTransaction#setMaxLifecycle}.
         *
         * @param fragment Fragment to have its state capped
         * @param maxLifecycleState Ceiling state for the fragment
         * @return Listener called after the operation
         */
        @NonNull
        public OnPostEventListener onFragmentMaxLifecyclePreUpdated(@NonNull Fragment fragment,
                @NonNull Lifecycle.State maxLifecycleState) {
            return NO_OP;
        }

        /**
         * Callback returned by {@link #onFragmentPreAdded}, {@link #onFragmentPreRemoved},
         * {@link #onFragmentMaxLifecyclePreUpdated} called after the operation ends.
         */
        public interface OnPostEventListener {
            /** Called after the operation is ends. */
            void onPost();
        }
    }

    /**
     * Registers a {@link FragmentTransactionCallback} to listen to fragment lifecycle changes
     * that happen inside the adapter.
     *
     * @param callback Callback to register
     */
    public void registerFragmentTransactionCallback(@NonNull FragmentTransactionCallback callback) {
        mFragmentEventDispatcher.registerCallback(callback);
    }

    /**
     * Unregisters a {@link FragmentTransactionCallback}.
     *
     * @param callback Callback to unregister
     * @see #registerFragmentTransactionCallback
     */
    public void unregisterFragmentTransactionCallback(
            @NonNull FragmentTransactionCallback callback) {
        mFragmentEventDispatcher.unregisterCallback(callback);
    }

    @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
    public @interface ExperimentalFragmentStateAdapterApi { }
}
