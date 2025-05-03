//gemini key
package com.example.langhexx.View;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.Controller.ChatAdapter;
import com.example.langhexx.Model.ChatMessage;
import com.example.langhexx.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatFragment extends Fragment {

    private EditText edtMessage;
    private ImageButton btnSend;
    private RecyclerView recyclerChat;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private TextView sampleQuestion1, sampleQuestion2, sampleQuestion3;
    private LinearLayout sampleQuestionsContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        //
        sampleQuestion1 = view.findViewById(R.id.sampleQuestion1);
        sampleQuestion2 = view.findViewById(R.id.sampleQuestion2);
        sampleQuestion3 = view.findViewById(R.id.sampleQuestion3);
        sampleQuestionsContainer = view.findViewById(R.id.sampleQuestionsContainer);
        setupSampleQuestionClickListener(sampleQuestion1);
        setupSampleQuestionClickListener(sampleQuestion2);
        setupSampleQuestionClickListener(sampleQuestion3);
        //
        edtMessage = view.findViewById(R.id.edtMessage);
        btnSend = view.findViewById(R.id.btnSend);
        recyclerChat = view.findViewById(R.id.recyclerChat);

        chatAdapter = new ChatAdapter(messages);
        recyclerChat.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerChat.setAdapter(chatAdapter);

        btnSend.setOnClickListener(v -> {
            String userMessage = edtMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(userMessage)) {
                // Hide the sample questions when the user sends a message
                if (sampleQuestionsContainer.getVisibility() == View.VISIBLE) {
                    sampleQuestionsContainer.setVisibility(View.GONE);
                }
                ChatMessage userMsg = new ChatMessage(userMessage, ChatMessage.SENDER_USER);
                messages.add(userMsg);
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerChat.scrollToPosition(messages.size() - 1);
                callGeminiAPI(userMessage);
                edtMessage.setText("");
            }
        });

        return view;
    }

    private void setupSampleQuestionClickListener(TextView sampleQuestion) {
        sampleQuestion.setOnClickListener(v -> {
            String questionText = sampleQuestion.getText().toString();
            edtMessage.setText(questionText);
            edtMessage.setSelection(questionText.length()); // Move cursor to end
            btnSend.performClick(); // Simulate send button click
            sampleQuestionsContainer.setVisibility(View.GONE);
        });
    }


    private void callGeminiAPI(String userMessage) {
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");

        JSONObject requestBody = new JSONObject();

        try {
            // Tạo mảng "parts"
            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", userMessage);
            parts.put(textPart);

            // Tạo mảng "contents"
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            contentObj.put("parts", parts);
            contents.put(contentObj);

            requestBody.put("contents", contents);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(mediaType, requestBody.toString());
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIzaSyDoQKvSTwu_RJMIKl3c456iLFW0oIK16tc")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Lỗi kết nối Gemini API", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseString = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseString);

                        JSONArray candidates = jsonResponse.getJSONArray("candidates");
                        JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
                        JSONArray parts = content.getJSONArray("parts");
                        String reply = parts.getJSONObject(0).getString("text");

                        requireActivity().runOnUiThread(() -> {
                            ChatMessage botMsg = new ChatMessage(reply, ChatMessage.SENDER_AI);
                            messages.add(botMsg);
                            chatAdapter.notifyItemInserted(messages.size() - 1);
                            recyclerChat.scrollToPosition(messages.size() - 1);
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "Lỗi xử lý phản hồi Gemini", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Phản hồi không hợp lệ từ Gemini", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

}

