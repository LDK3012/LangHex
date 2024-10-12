package com.example.langhexx.View;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.example.langhexx.Model.Levels;
import com.example.langhexx.R;

import java.util.ArrayList;

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
        addControls(view);
        setUpListView();
        return view;
    }

    public void addControls(View view){
        lstLevel = view.findViewById(R.id.lstLevels);
        btnNotifiy = view.findViewById(R.id.btnNotifications);
        txtName = view.findViewById(R.id.txtName);
        imgAvatar = view.findViewById(R.id.imgAvatar);
    }

    private void setUpListView(){
        levelsList = new ArrayList<>();
        levelsList.add(new Levels("Beginner", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("Beginner", String.valueOf(R.drawable.example)));
        levelsAdapter = new LevelsAdapter(getActivity(), R.layout.custom_levels_lst, levelsList);
        lstLevel.setAdapter(levelsAdapter);
    }


}