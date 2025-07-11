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

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

/**
 * {@link ViewHolder} implementation for handling {@link Fragment}s. Used in
 * {@link _FragmentListAdapter}.
 */
public final class FragmentListViewHolder extends ViewHolder {
    private FragmentListViewHolder(@NonNull FrameLayout container) {
        super(container);
    }

    @NonNull
    static FragmentListViewHolder create(@NonNull ViewGroup parent, @RecyclerView.Orientation int orientation) {
        FrameLayout container = new FrameLayout(parent.getContext());
        if (orientation == RecyclerView.VERTICAL) {
            container.setLayoutParams(
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            container.setLayoutParams(
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }
        container.setId(View.generateViewId());
        container.setSaveEnabled(false);
        return new FragmentListViewHolder(container);
    }

    @NonNull
    FrameLayout getContainer() {
        return (FrameLayout) itemView;
    }
}
