package com.example.langhexx.View;

import android.content.Intent;
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
        levelsList.add(new Levels("General English - Vstep", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A1", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A2", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A3", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A4", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A5", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A6", String.valueOf(R.drawable.example)));
        levelsList.add(new Levels("A7", String.valueOf(R.drawable.example)));
        levelsAdapter = new LevelsAdapter(getActivity(), R.layout.custom_levels_lst, levelsList);
        lstLevel.setAdapter(levelsAdapter);
        lstLevel.setOnItemClickListener((parent, view, position, id) -> {
            String levelName = levelsList.get(position).getTxtLevels() ;
            if(levelName.equals("General English - Vstep")){
                Intent intent = new Intent(getActivity(), VstepActivity.class) ;
                startActivity(intent);
            }else {
                Intent intent = new Intent(getActivity(), LearningTypeActivity.class);
                intent.putExtra("levelName", levelsList.get(position).getTxtLevels());
                startActivity(intent);
            }
        });
    }
}