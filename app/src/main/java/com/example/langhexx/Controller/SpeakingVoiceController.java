package com.example.langhexx.Controller;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.langhexx.Model.SpeakingContract;
import com.example.langhexx.Model.SpeakingVoiceModel;

import java.util.List;
public class SpeakingVoiceController  {

    private static final String TAG = "SpeakingPronunciationCtrl";

    private SpeakingContract.Model model;
    private SpeakingContract.View view;
    private Handler handler = new Handler(Looper.getMainLooper());

    private String levelName;
    private String topicId;
    private boolean ttsReady = false;
    private String currentScript;

    public SpeakingVoiceController(SpeakingContract.View view, Context context, String levelName, String topicId) {
        this.view = view;
        this.model = new SpeakingVoiceModel(context);
        this.levelName = levelName;
        this.topicId = topicId;

        if (levelName == null || topicId == null || levelName.isEmpty() || topicId.isEmpty()) {
            if (view != null) {
                view.showError("Missing Level or Topic ID information for Pronunciation !");
                view.setMicButtonEnabled(false);
            } else {
                Log.e(TAG, "View is null in constructor for Pronunciation.");
            }
        }
    }

}