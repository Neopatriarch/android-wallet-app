/*
 * Copyright (C) 2017 IOTA Foundation
 *
 * Authors: pinpong, adrianziser, saschan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.iota.wallet.ui.fragment;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.greenrobot.eventbus.Subscribe;
import org.iota.wallet.IOTA;
import org.iota.wallet.R;
import org.iota.wallet.api.TaskManager;
import org.iota.wallet.databinding.FragmentWalletAddressesBinding;
import org.iota.wallet.helper.Constants;
import org.iota.wallet.model.api.requests.GetAccountDataRequest;
import org.iota.wallet.model.api.requests.GetNewAddressRequest;
import org.iota.wallet.model.api.requests.NodeInfoRequest;
import org.iota.wallet.model.api.requests.SendTransferRequest;
import org.iota.wallet.model.api.responses.GetAccountDataResponse;
import org.iota.wallet.model.api.responses.GetNewAddressResponse;
import org.iota.wallet.model.api.responses.NodeInfoResponse;
import org.iota.wallet.model.api.responses.SendTransferResponse;
import org.iota.wallet.model.api.responses.error.NetworkError;
import org.iota.wallet.ui.adapter.WalletAddressCardAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WalletAddressesFragment extends BaseSwipeRefreshLayoutFragment implements WalletTabFragment.OnFabClickListener {

    private static final String ADDRESSES_LIST = "addresses";
    private FragmentWalletAddressesBinding addressBinding;
    private WalletAddressCardAdapter adapter;
    private RecyclerView recyclerView;
    private List<String> addresses;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        addressBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_wallet_addresses, container, false);
        View view = addressBinding.getRoot();
        swipeRefreshLayout = view.findViewById(R.id.wallet_addresses_swipe_container);
        recyclerView = view.findViewById(R.id.wallet_addresses_recycler_view);

        return view;
    }

    private void setAdapter() {
        if (addresses == null) {
            addresses = new ArrayList<>();
        }

        adapter = new WalletAddressCardAdapter(getActivity(), addresses);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        addressBinding.setAddresses(addresses);
    }

    private void generateNewAddress() {
        TaskManager rt = new TaskManager(getActivity());
        GetNewAddressRequest gtr = new GetNewAddressRequest();
        gtr.setSeed(String.valueOf(IOTA.seed));
        rt.startNewRequestTask(gtr);
    }

    private void attachNewAddress(String address) {
        //0 value transfer is required to attachToTangle
        TaskManager rt = new TaskManager(getActivity());
        SendTransferRequest tir = new SendTransferRequest(address, "0", "", Constants.NEW_ADDRESS_TAG);
        rt.startNewRequestTask(tir);
        if (!swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.post(new Runnable() {
                @Override
                public void run() {
                    swipeRefreshLayout.setRefreshing(true);
                }
            });
        }
    }

    @Subscribe
    public void onEvent(GetNewAddressResponse gnar) {
        swipeRefreshLayout.setRefreshing(false);
        //attach new
        attachNewAddress(gnar.getAddresses().get(0));
    }

    @Subscribe
    public void onEvent(SendTransferResponse str) {
        if (Arrays.asList(str.getSuccessfully()).contains(true))
            getAccountData();
    }

    @Subscribe
    public void onEvent(GetAccountDataResponse gad) {
        swipeRefreshLayout.setRefreshing(false);
        addresses = gad.getAddresses();
        adapter.setAdapterList(addresses);
        setAdapter();
    }

    private void getAccountData() {
        TaskManager rt = new TaskManager(getActivity());
        GetAccountDataRequest gna = new GetAccountDataRequest();
        rt.startNewRequestTask(gna);
    }

    @Subscribe
    public void onEvent(NodeInfoResponse nodeInfoResponse) {
        if (nodeInfoResponse.getLatestMilestoneIndex() == (nodeInfoResponse.getLatestSolidSubtangleMilestoneIndex())) {
            getAccountData();
        } else {
            swipeRefreshLayout.setRefreshing(false);
            Snackbar.make(getActivity().findViewById(R.id.drawer_layout), getString(R.string.messages_not_fully_synced_yet), Snackbar.LENGTH_LONG).show();
        }
    }

    private void getNodeInfo() {
        TaskManager rt = new TaskManager(getActivity());
        NodeInfoRequest nir = new NodeInfoRequest();
        rt.startNewRequestTask(nir);

        if (!swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.post(new Runnable() {
                @Override
                public void run() {
                    swipeRefreshLayout.setRefreshing(true);
                }
            });
        }
    }

    public void onFabClick() {
        generateNewAddress();
    }

    @Subscribe
    public void onEvent(NetworkError error) {
        switch (error.getErrorType()) {
            case ACCESS_ERROR:
                swipeRefreshLayout.setRefreshing(false);
                getNodeInfo();
                break;
            case REMOTE_NODE_ERROR:
                swipeRefreshLayout.setRefreshing(false);
                addresses.clear();
                setAdapter();
                break;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (addresses != null)
            outState.putStringArrayList(ADDRESSES_LIST, (ArrayList<String>) addresses);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            if (addresses == null) {
                addresses = new ArrayList<>();
            }
            addresses = savedInstanceState.getStringArrayList(ADDRESSES_LIST);
        }
        setAdapter();
    }

    @Override
    public void onRefresh() {
        super.onRefresh();
        getNodeInfo();
    }

    @Override
    public void onResume() {
        super.onResume();
        getNodeInfo();
    }
}
