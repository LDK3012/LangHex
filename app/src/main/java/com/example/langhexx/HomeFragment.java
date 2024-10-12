package com.example.langhexx;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    private ListView lstLevel;
    private Button btnNotifiy;
    private TextView txtName;
    private ImageView imgAvatar;
    private ArrayList<Levels> levelsList;
    private LevelsAdapter levelsAdapter;

    public HomeFragment(){

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_fragement, container, false);
        addControls();
        setUpListView();
        return view;
    }

    public void addControls(){
        lstLevel = getView().findViewById(R.id.lstLevels);
        btnNotifiy = getView().findViewById(R.id.btnNotifications);
        txtName = getView().findViewById(R.id.txtName);
        imgAvatar = getView().findViewById(R.id.imgAvatar);
    }

    private void setUpListView(){
        levelsList = new ArrayList<>();
        levelsAdapter = new LevelsAdapter(getActivity(), R.layout.custom_levels_lst, levelsList);
        lstLevel.setAdapter(levelsAdapter);
    }


}