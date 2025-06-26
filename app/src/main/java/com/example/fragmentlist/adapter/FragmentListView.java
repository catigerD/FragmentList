package com.example.fragmentlist.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

public class FragmentListView extends FrameLayout {

    private RecyclerView mRecyclerView;
    private Parcelable mPendingAdapterState;

    public FragmentListView(@NonNull Context context) {
        super(context);
        initialize(context, null);
    }

    public FragmentListView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs);
    }

    public FragmentListView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs);
    }

    private void initialize(Context context, AttributeSet attrs) {
        mRecyclerView = new RecyclerView(context);
        mRecyclerView.setId(View.generateViewId());
        mRecyclerView.setLayoutParams(
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mRecyclerView, mRecyclerView.getLayoutParams());
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    @SuppressWarnings("ConstantConditions")
    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);

        ss.mRecyclerViewId = mRecyclerView.getId();

        if (mPendingAdapterState != null) {
            ss.mAdapterState = mPendingAdapterState;
        } else {
            RecyclerView.Adapter<?> adapter = mRecyclerView.getAdapter();
            if (adapter instanceof StatefulAdapter) {
                ss.mAdapterState = ((StatefulAdapter) adapter).saveState();
            }
        }

        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mPendingAdapterState = ss.mAdapterState;
    }

    private void restorePendingState() {
        RecyclerView.Adapter<?> adapter = mRecyclerView.getAdapter();
        if (adapter == null) {
            return;
        }
        if (mPendingAdapterState != null) {
            if (adapter instanceof StatefulAdapter) {
                ((StatefulAdapter) adapter).restoreState(mPendingAdapterState);
            }
            mPendingAdapterState = null;
        }
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // RecyclerView changed an id, so we need to reflect that in the saved state
        Parcelable state = container.get(getId());
        if (state instanceof SavedState) {
            final int previousRvId = ((SavedState) state).mRecyclerViewId;
            final int currentRvId = mRecyclerView.getId();
            container.put(currentRvId, container.get(previousRvId));
            container.remove(previousRvId);
        }

        super.dispatchRestoreInstanceState(container);

        // State of ViewPager2 and its child (RecyclerView) has been restored now
        restorePendingState();
    }

    static class SavedState extends BaseSavedState {
        int mRecyclerViewId;
        Parcelable mAdapterState;

        @RequiresApi(24)
        @SuppressLint("ClassVerificationFailure")
        SavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            readValues(source, loader);
        }

        SavedState(Parcel source) {
            super(source);
            readValues(source, null);
        }

        SavedState(Parcelable superState) {
            super(superState);
        }

        @SuppressWarnings("deprecation")
        private void readValues(Parcel source, ClassLoader loader) {
            mRecyclerViewId = source.readInt();
            mAdapterState = source.readParcelable(loader);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mRecyclerViewId);
            out.writeParcelable(mAdapterState, flags);
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel source, ClassLoader loader) {
                return Build.VERSION.SDK_INT >= 24
                        ? new SavedState(source, loader)
                        : new SavedState(source);
            }

            @Override
            public SavedState createFromParcel(Parcel source) {
                return createFromParcel(source, null);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
