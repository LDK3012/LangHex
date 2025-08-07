package com.example.langhexx.View;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.langhexx.BuildConfig;
import com.example.langhexx.Controller.ChatAdapter;
import com.example.langhexx.Model.ChatMessage;
import com.example.langhexx.Model.ChatHistoryManager;
import com.example.langhexx.Model.CustomToast;
import com.example.langhexx.Model.TokenLimiter;
import com.example.langhexx.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
    private boolean isLimitToastVisible = false;
    private static final String OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;
    private static final String TYPING_MESSAGE = "Typing...";
    public interface KeyboardVisibilityListener {
        void onKeyboardVisibilityChanged(boolean isVisible);
    }

    private KeyboardVisibilityListener keyboardVisibilityListener;
    private EditText edtMessage;
    private ImageButton btnSend;
    private RecyclerView recyclerChat;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private TextView sampleQuestion1, sampleQuestion2, sampleQuestion3;
    private LinearLayout sampleQuestionsContainer;
    private TextView selectedModelText, tvIntroduce;
    private String currentModel = "Normal Mode";

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

        selectedModelText = view.findViewById(R.id.selectedModelText);
        selectedModelText.setText(currentModel);
        selectedModelText.setOnClickListener(this::showModelMenu);

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
        recyclerChat = view.findViewById(R.id.recyclerChat);

        chatAdapter = new ChatAdapter(messages);
        recyclerChat.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerChat.setAdapter(chatAdapter);

        btnSend.setOnClickListener(v -> {
            String userMessage = edtMessage.getText().toString().trim();
            if (!TextUtils.isEmpty(userMessage)) {
                if ("Pro Mode".equals(currentModel) && !TokenLimiter.canSend(getContext())) {
                    if (!isLimitToastVisible) {
                        isLimitToastVisible = true;
                        int[] location = new int[2];
                        edtMessage.getLocationOnScreen(location);
                        int toastY = location[1] - 100;
                        Toast toast = new Toast(getContext());
                        View layout = inflater.inflate(R.layout.limted_custom_toast, null);

                        TextView text = layout.findViewById(R.id.toastText);
                        text.setText("You have reached the limit of 10 messages per hour in Pro Mode ! please switch to Normal Mode");

                        layout.setBackgroundResource(R.drawable.limited_toast_background);

                        toast.setView(layout);
                        toast.setDuration(Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, toastY);
                        toast.show();
                        edtMessage.postDelayed(() -> isLimitToastVisible = false, 2000);
                    }
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
                if ("Pro Mode".equals(currentModel)) {
                    callOpenAIAPI(userMessage);
                } else {
                    callGeminiAPI(userMessage);
                }
                tvIntroduce.setVisibility(View.INVISIBLE);
                edtMessage.setText("");
            }
        });

        List<ChatMessage> savedMessages = ChatHistoryManager.loadChatHistory(getContext());
        if (!savedMessages.isEmpty()) {
            tvIntroduce.setVisibility(View.INVISIBLE);
            messages.addAll(savedMessages);
            chatAdapter.notifyDataSetChanged();
            recyclerChat.scrollToPosition(messages.size() - 1);
            sampleQuestionsContainer.setVisibility(View.GONE);
        }

        return view;
    }

    private void showModelMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        Menu menu = popup.getMenu();
        menu.add(Menu.NONE, 1, 1, "Normal Mode").setIcon(R.drawable.lightning);
        menu.add(Menu.NONE, 2, 2, "Pro Mode").setIcon(R.drawable.star);
        List<ChatMessage> savedMessages = ChatHistoryManager.loadChatHistory(getContext());
        if (!savedMessages.isEmpty()) {
            menu.add(Menu.NONE, 3, 3, "Clear History").setIcon(R.drawable.clear);
        }
        try {
            Field mField = popup.getClass().getDeclaredField("mPopup");
            mField.setAccessible(true);
            Object menuPopupHelper = mField.get(popup);
            Method setForceIcons = menuPopupHelper.getClass().getDeclaredMethod("setForceShowIcon", boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                case 2:
                    currentModel = item.getTitle().toString();
                    selectedModelText.setText(currentModel);
                    return true;
                case 3:
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Confirm")
                            .setMessage("Are you sure you want to delete all chat history?")
                            .setPositiveButton("Yes", (dialog, which) -> {
                                ChatHistoryManager.clearChatHistory(getContext());
                                messages.clear();
                                chatAdapter.notifyDataSetChanged();
                                Toast.makeText(getContext(), "Chat history cleared.", Toast.LENGTH_SHORT).show();
                                sampleQuestionsContainer.setVisibility(View.VISIBLE);
                                tvIntroduce.setVisibility(View.VISIBLE);
                            })
                            .setNegativeButton("No", null)
                            .show();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
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
            tvIntroduce.setVisibility(View.INVISIBLE);
            String questionText = sampleQuestion.getText().toString();
            edtMessage.setText(questionText);
            edtMessage.setSelection(questionText.length());
            btnSend.performClick();
            sampleQuestionsContainer.setVisibility(View.GONE);
        });
    }

    private void callGeminiAPI(String userMessage) {
        ChatMessage typingMsg = new ChatMessage(TYPING_MESSAGE, ChatMessage.SENDER_AI);
        messages.add(typingMsg);
        requireActivity().runOnUiThread(() -> {
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerChat.scrollToPosition(messages.size() - 1);
        });

        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject requestBody = new JSONObject();
        try {
            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", userMessage);
            parts.put(textPart);
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
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + BuildConfig.GEMINI_API_KEY)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    removeTypingMessage();
                    Toast.makeText(getContext(), "Gemini API connection error.", Toast.LENGTH_SHORT).show();
                });
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
                        JSONArray candidates = jsonResponse.getJSONArray("candidates");
                        JSONObject firstCandidate = candidates.getJSONObject(0);
                        JSONObject content = firstCandidate.getJSONObject("content");
                        JSONArray parts = content.getJSONArray("parts");
                        JSONObject firstPart = parts.getJSONObject(0);

                        String rawReply = firstPart.getString("text");
                        String[] lines = rawReply.replace("*", "").split("\n");
                        StringBuilder sb = new StringBuilder();
                        for (String line : lines) {
                            sb.append(line.replaceAll("^#+\\s*", "")).append("\n");
                        }
                        String processedReply = sb.toString()
                                .replaceAll("(?<!\\n)\\n(?!\\n)", "\n\n")
                                .replace(". ", ".\n")
                                .trim();

                        requireActivity().runOnUiThread(() -> {
                            removeTypingMessage();
                            ChatMessage botMsg = new ChatMessage(processedReply, ChatMessage.SENDER_AI);
                            messages.add(botMsg);
                            chatAdapter.notifyItemInserted(messages.size() - 1);
                            recyclerChat.scrollToPosition(messages.size() - 1);
                            ChatHistoryManager.saveChatHistory(getContext(), messages);
                        });
                    } catch (JSONException e) {
                        handleApiResponseError("Error parsing Gemini JSON response");
                    }
                } else {
                    handleApiResponseError("Invalid response from Gemini (Code: " + response.code() + ")");
                }
            }
        });
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

    private void callOpenAIAPI(String userMessage) {
        ChatMessage typingMsg = new ChatMessage(TYPING_MESSAGE, ChatMessage.SENDER_AI);
        messages.add(typingMsg);
        requireActivity().runOnUiThread(() -> {
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerChat.scrollToPosition(messages.size() - 1);
        });

        OkHttpClient client = new OkHttpClient();

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

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                requireActivity().runOnUiThread(() -> {
                    removeTypingMessage();
                    Toast.makeText(getContext(), "OpenAI API connection error.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isAdded() || getActivity() == null) {
                    if (response.body() != null) response.body().close();
                    return;
                }
                String responseBodyString = response.body() != null ? response.body().string() : null;
                if (response.body() != null) response.body().close();

                if (response.isSuccessful() && responseBodyString != null) {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBodyString);
                        JSONArray choices = jsonResponse.getJSONArray("choices");
                        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                        String aiReply = message.getString("content").trim();
                        String[] lines = aiReply.replace("*", "").split("\n");
                        StringBuilder sb = new StringBuilder();
                        for (String line : lines) {
                            sb.append(line.replaceAll("^#+\\s*", "")).append("\n");
                        }
                        String processedReply = sb.toString()
                                .replaceAll("(?<!\\n)\\n(?!\\n)", "\n\n")
                                .replace(". ", ".\n")
                                .trim();
                        requireActivity().runOnUiThread(() -> {
                            removeTypingMessage();
                            ChatMessage botMsg = new ChatMessage(processedReply, ChatMessage.SENDER_AI);
                            messages.add(botMsg);
                            chatAdapter.notifyItemInserted(messages.size() - 1);
                            recyclerChat.scrollToPosition(messages.size() - 1);
                            ChatHistoryManager.saveChatHistory(getContext(), messages);
                        });
                    } catch (JSONException e) {
                        handleApiResponseError("Error parsing OpenAI JSON response");
                    }
                } else {
                    handleApiResponseError("Invalid response from OpenAI (Code: " + response.code() + ")");
                }
            }
        });
    }

    private void handleApiResponseError(String logMessage) {
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Error!", Toast.LENGTH_SHORT).show()
            );
        }
    }

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
