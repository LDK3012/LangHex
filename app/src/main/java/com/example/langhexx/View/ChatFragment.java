
package com.example.langhexx.View;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
    private View rootView;
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
    private boolean isKeyboardVisible = false;
    public interface KeyboardVisibilityListener {
        void onKeyboardVisibilityChanged(boolean isVisible);
    }
    private KeyboardVisibilityListener keyboardVisibilityListener ;


    private EditText edtMessage;
    private ImageButton btnSend;
    private RecyclerView recyclerChat;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private TextView sampleQuestion1, sampleQuestion2, sampleQuestion3;
    private LinearLayout sampleQuestionsContainer;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = requireActivity().getWindow().getDecorView();
        setupKeyboardListener();
    }

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

    private void setupKeyboardListener() {
        globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private final Rect r = new Rect();
            private final int threshold = calculateThreshold();

            @Override
            public void onGlobalLayout() {
                if (rootView == null || keyboardVisibilityListener == null) return;
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;
                boolean currentlyVisible = keypadHeight > threshold;
                if (currentlyVisible != isKeyboardVisible) {
                    isKeyboardVisible = currentlyVisible;
                    keyboardVisibilityListener.onKeyboardVisibilityChanged(isKeyboardVisible);
                }
            }
        };
    }


    private int calculateThreshold() {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, getResources().getDisplayMetrics());
    }

    private void setupSampleQuestionClickListener(TextView sampleQuestion) {
        sampleQuestion.setOnClickListener(v -> {
            String questionText = sampleQuestion.getText().toString();
            edtMessage.setText(questionText);
            edtMessage.setSelection(questionText.length());
            btnSend.performClick();
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
                        Toast.makeText(getContext(), "Gemini API connection error", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded() || getActivity() == null) {
                    if (response.body() != null) response.body().close();
                    return;
                }

                final String responseBodyString = response.body() != null ? response.body().string() : null;
                if (response.body() != null) response.body().close();

                if (response.isSuccessful() && responseBodyString != null) {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBodyString);
                        if (!jsonResponse.has("candidates") || jsonResponse.getJSONArray("candidates").length() == 0) {
                            handleApiResponseError("Invalid response structure: Missing or empty 'candidates'");
                            return;
                        }
                        JSONArray candidates = jsonResponse.getJSONArray("candidates");

                        JSONObject firstCandidate = candidates.getJSONObject(0);
                        if (!firstCandidate.has("content")) {
                            handleApiResponseError("Invalid response structure: Missing 'content'");
                            return;
                        }
                        JSONObject content = firstCandidate.getJSONObject("content");

                        if (!content.has("parts") || content.getJSONArray("parts").length() == 0) {
                            handleApiResponseError("Invalid response structure: Missing or empty 'parts'");
                            return;
                        }
                        JSONArray parts = content.getJSONArray("parts");

                        JSONObject firstPart = parts.getJSONObject(0);
                        if (!firstPart.has("text")) {
                            handleApiResponseError("Invalid response structure: Missing 'text'");
                            return;
                        }

                        String rawReply = firstPart.getString("text"); // Lấy phản hồi gốc
                        String processedReply = rawReply.replace("*", "").trim();
                        requireActivity().runOnUiThread(() -> {
                            if (isAdded() && getActivity() != null) {
                                ChatMessage botMsg = new ChatMessage(processedReply, ChatMessage.SENDER_AI);
                                messages.add(botMsg);
                                chatAdapter.notifyItemInserted(messages.size() - 1);
                                recyclerChat.scrollToPosition(messages.size() - 1);
                            }
                        });

                    } catch (JSONException e) {
                        handleApiResponseError("Lỗi xử lý JSON phản hồi Gemini");
                    }
                } else {
                    String errorBody = "";
                    try {
                        if (responseBodyString == null && response.body() != null) {
                            errorBody = response.body().string(); // Đọc body lỗi
                            response.body().close(); // Đóng body lỗi
                        } else if (responseBodyString != null) {
                            errorBody = responseBodyString; // Dùng lại body đã đọc nếu response không successful
                        }
                    } catch (Exception ignored) { } // Bỏ qua lỗi đọc body lỗi
                    handleApiResponseError("Phản hồi không hợp lệ từ Gemini (Code: " + response.code() + ")");
                }
            }

        });
    }

    private void handleApiResponseError(String logMessage) {
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Gemini respond error!", Toast.LENGTH_SHORT).show() // Thông báo chung cho người dùng
            );
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof KeyboardVisibilityListener) {
            keyboardVisibilityListener = (KeyboardVisibilityListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement KeyboardVisibilityListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        keyboardVisibilityListener = null ;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (rootView != null && globalLayoutListener != null) {
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (rootView != null && globalLayoutListener != null) {
            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
        }
    }
}

