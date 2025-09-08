package vn.enghexteam.enghex.View;

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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import vn.enghexteam.enghex.BuildConfig;
import vn.enghexteam.enghex.Controller.ChatAdapter;
import vn.enghexteam.enghex.Model.ChatHistoryManager;
import vn.enghexteam.enghex.Model.ChatMessage;
import vn.enghexteam.enghex.Model.TokenLimiter;
import vn.enghexteam.enghex.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;


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

    private static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;
    private static final String TYPING_MESSAGE = "Typing...";
    private volatile boolean isSending = false;

    public interface KeyboardVisibilityListener {
        void onKeyboardVisibilityChanged(boolean isVisible);
    }

    private KeyboardVisibilityListener keyboardVisibilityListener;
    private EditText edtMessage;
    private ImageButton btnSend;
    private ImageButton btnClearHistory;
    private RecyclerView recyclerChat;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private TextView sampleQuestion1, sampleQuestion2, sampleQuestion3;
    private LinearLayout sampleQuestionsContainer;
    private TextView selectedModelText, tvIntroduce;
    private final String currentModel = "Pro Mode";
    private boolean isLimitToastVisible = false;
    private OkHttpClient httpClient;

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
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .writeTimeout(35, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        selectedModelText = view.findViewById(R.id.selectedModelText);
        selectedModelText.setText(currentModel);
        selectedModelText.setVisibility(View.GONE);

        tvIntroduce = view.findViewById(R.id.tvIntroduce);

        sampleQuestion1 = view.findViewById(R.id.sampleQuestion1);
        sampleQuestion2 = view.findViewById(R.id.sampleQuestion2);
        sampleQuestion3 = view.findViewById(R.id.sampleQuestion3);
        sampleQuestionsContainer = view.findViewById(R.id.sampleQuestionsContainer);

        setupSampleQuestionClickListener(sampleQuestion1);
        setupSampleQuestionClickListener(sampleQuestion2);
        setupSampleQuestionClickListener(sampleQuestion3);

        edtMessage = view.findViewById(R.id.edtMessage);
        btnSend = view.findViewById(R.id.btnSend);
        btnClearHistory = view.findViewById(R.id.btnClearHistory);
        recyclerChat = view.findViewById(R.id.recyclerChat);

        chatAdapter = new ChatAdapter(messages);
        recyclerChat.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerChat.setAdapter(chatAdapter);

        btnSend.setOnClickListener(v -> {
            String userMessage = edtMessage.getText().toString();
            sendMessage(userMessage);
        });

        btnClearHistory.setOnClickListener(v -> {
            if (messages.isEmpty()) {
                Toast.makeText(getContext(), "Nothing to delete", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirmation")
                    .setMessage("Delete all chat history")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        ChatHistoryManager.clearChatHistory(getContext());
                        messages.clear();
                        chatAdapter.notifyDataSetChanged();
                        Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                        sampleQuestionsContainer.setVisibility(View.VISIBLE);
                        tvIntroduce.setVisibility(View.VISIBLE);
                        updateClearButtonState();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        List<ChatMessage> savedMessages = ChatHistoryManager.loadChatHistory(getContext());
        if (!savedMessages.isEmpty()) {
            tvIntroduce.setVisibility(View.INVISIBLE);
            messages.addAll(savedMessages);
            chatAdapter.notifyDataSetChanged();
            recyclerChat.scrollToPosition(messages.size() - 1);
            sampleQuestionsContainer.setVisibility(View.GONE);
        }
        updateClearButtonState();

        return view;
    }

    private void hideKeyboard() {
        View v = requireActivity().getCurrentFocus();
        if (v == null) v = edtMessage;
        if (v != null) {
            v.clearFocus();
            InputMethodManager imm =
                    (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        if (recyclerChat != null) recyclerChat.requestFocus();
    }


    private void updateClearButtonState() {
        if (btnClearHistory != null) {
            btnClearHistory.setEnabled(!messages.isEmpty());
            btnClearHistory.setAlpha(!messages.isEmpty() ? 1f : 0.4f);
        }
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
            String questionText = sampleQuestion.getText().toString().trim();
            sendMessage(questionText);
        });
    }

    private void sendMessage(String rawUserMessage) {
        String userMessage = rawUserMessage == null ? "" : rawUserMessage.trim();
        if (TextUtils.isEmpty(userMessage)) return;
        hideKeyboard();
        if (isSending) return; // debounce
        isSending = true;

        if (!TokenLimiter.canSend(getContext())) {
            if (!isLimitToastVisible) {
                isLimitToastVisible = true;
                Toast.makeText(
                        getContext(),
                        "You have reached the limit of 20 msg/hour. Please try again later.",
                        Toast.LENGTH_SHORT
                ).show();
                edtMessage.postDelayed(() -> isLimitToastVisible = false, 2000);
            }
            isSending = false;
            return;
        }

        if (sampleQuestionsContainer.getVisibility() == View.VISIBLE) {
            sampleQuestionsContainer.setVisibility(View.GONE);
        }

        ChatMessage userMsg = new ChatMessage(userMessage, ChatMessage.SENDER_USER);
        messages.add(userMsg);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.scrollToPosition(messages.size() - 1);
        ChatHistoryManager.saveChatHistory(getContext(), messages);
        updateClearButtonState();

        tvIntroduce.setVisibility(View.INVISIBLE);
        edtMessage.setText("");

        callOpenAIAPI(userMessage);
    }

    private void callOpenAIAPI(String userMessage) {
        // Hiển thị "Typing..."
        ChatMessage typingMsg = new ChatMessage(TYPING_MESSAGE, ChatMessage.SENDER_AI);
        messages.add(typingMsg);
        requireActivity().runOnUiThread(() -> {
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerChat.scrollToPosition(messages.size() - 1);
        });

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("model", "gpt-4.1");
            JSONArray messagesArr = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messagesArr.put(userMsg);
            requestBody.put("messages", messagesArr);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), requestBody.toString());

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        removeTypingMessage();
                        Toast.makeText(getContext(), "Connection error", Toast.LENGTH_SHORT).show();
                        isSending = false;
                    });
                } else {
                    isSending = false;
                }
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (!isAdded() || getActivity() == null) {
                        if (response.body() != null) response.body().close();
                        return;
                    }
                    String responseBodyString = response.body() != null ? response.body().string() : null;
                    if (response.body() != null) response.body().close();

                    if (!response.isSuccessful() || responseBodyString == null) {
                        uiFailOpenAI("Invalid response (Code: " + response.code() + ")");
                        return;
                    }

                    JSONObject jsonResponse = new JSONObject(responseBodyString);
                    JSONArray choices = jsonResponse.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        uiFailOpenAI("Error");
                        return;
                    }

                    JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                    if (message == null) {
                        uiFailOpenAI("Error");
                        return;
                    }

                    String aiReply = message.optString("content", "").trim();
                    if (aiReply.isEmpty()) {
                        uiFailOpenAI("Error");
                        return;
                    }

                    String processedReply = postProcess(aiReply);
                    requireActivity().runOnUiThread(() -> {
                        removeTypingMessage();
                        ChatMessage botMsg = new ChatMessage(processedReply, ChatMessage.SENDER_AI);
                        messages.add(botMsg);
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        recyclerChat.scrollToPosition(messages.size() - 1);
                        ChatHistoryManager.saveChatHistory(getContext(), messages);
                        updateClearButtonState();
                        isSending = false;
                    });
                } catch (Exception ex) {
                    uiFailOpenAI("Error");
                }
            }

            private void uiFailOpenAI(String msg) {
                if (isAdded() && getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        removeTypingMessage();
                        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                        isSending = false;
                    });
                } else {
                    isSending = false;
                }
            }
        });
    }

    private String postProcess(String raw) {
        String[] lines = raw.replace("*", "").split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.replaceAll("^#+\\s*", "")).append("\n");
        }
        return sb.toString()
                .replaceAll("(?<!\\n)\\n(?!\\n)", "\n\n")
                .trim();
    }

    private void removeTypingMessage() {
        if (!messages.isEmpty()) {
            int lastIndex = messages.size() - 1;
            ChatMessage lastMsg = messages.get(lastIndex);
            if (lastMsg.getSenderType() == ChatMessage.SENDER_AI
                    && TYPING_MESSAGE.equals(lastMsg.getMessage())) {
                messages.remove(lastIndex);
                chatAdapter.notifyItemRemoved(lastIndex);
            }
        }
    }

    // =============== LIFECYCLE / KEYBOARD ===============
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof KeyboardVisibilityListener) {
            keyboardVisibilityListener = (KeyboardVisibilityListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement KeyboardVisibilityListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        keyboardVisibilityListener = null;
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
