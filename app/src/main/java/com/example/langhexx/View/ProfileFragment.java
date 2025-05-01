package com.example.langhexx.View;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.langhexx.Model.UsernamePasswordSessionManager;
import com.example.langhexx.R;

public class ProfileFragment extends Fragment {

    TextView txtProfileDetail, txtFeedback, txtName, txtEmail;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        addControls(view);
        //
        setName();
        addEvents();
        return view;
    }

    public void addControls(View view){
        txtProfileDetail = view.findViewById(R.id.txtInfo);
        txtFeedback = view.findViewById(R.id.txtFeedback);
        txtName = view.findViewById(R.id.txtName) ;
        txtEmail = view.findViewById(R.id.txtMail) ;
    }

    public void addEvents(){
        txtProfileDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), ProfileDetail.class);
                startActivity(intent);
            }
        });

        txtFeedback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), FeedbackActivity.class);
                startActivity(intent);
            }
        });
    }

    //
    private void setName(){
        UsernamePasswordSessionManager sessionManager = new UsernamePasswordSessionManager(getContext()) ;
        txtName.setText("Leaner");
        txtEmail.setText(sessionManager.getUsername());
    }

}