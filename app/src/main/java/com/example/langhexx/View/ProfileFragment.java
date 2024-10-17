package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.langhexx.R;

public class ProfileFragment extends Fragment {

    TextView txtProfileDetail, txtFeedback;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        addControls();
        addEvents();
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    public void addControls(){
        txtProfileDetail = getView().findViewById(R.id.txtInfo);
        txtFeedback = getView().findViewById(R.id.txtFeedback);
    }

    public void addEvents(){
        txtProfileDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), ProfileDetail.class);
                startActivity(intent);
            }
        });

        txtProfileDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), FeedbackActivity.class);
                startActivity(intent);
            }
        });
    }

}