package vn.enghexteam.enghex.View;

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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private RecyclerView recyclerChat;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private TextView sampleQuestion1, sampleQuestion2, sampleQuestion3;
    private LinearLayout sampleQuestionsContainer;
    private TextView selectedModelText, tvIntroduce;
    private String currentModel = "Normal Mode";
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
            String userMessage = edtMessage.getText().toString();
            sendMessage(userMessage);
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
        PopupMenu popup = new PopupMenu(requireContext(), anchor, Gravity.END);
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
            String questionText = sampleQuestion.getText().toString().trim();
            sendMessage(questionText);
        });
    }

    private void sendMessage(String rawUserMessage) {
        String userMessage = rawUserMessage == null ? "" : rawUserMessage.trim();
        if (TextUtils.isEmpty(userMessage)) return;
        if (isSending) return; // debounce
        isSending = true;

        if ("Pro Mode".equals(currentModel) && !TokenLimiter.canSend(getContext())) {
            if (!isLimitToastVisible) {
                isLimitToastVisible = true;
                Toast.makeText(
                        getContext(),
                        "You have reached the limit of 10 messages per hour in Pro Mode! Please switch to Normal Mode.",
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

        tvIntroduce.setVisibility(View.INVISIBLE);
        edtMessage.setText("");

        if ("Pro Mode".equals(currentModel)) {
            callOpenAIAPI(userMessage);
        } else {
            callGeminiAPI(userMessage);
        }
    }

    // =============== GEMINI ===============
    private void callGeminiAPI(String userMessage) {
        // Hiển thị "Typing..."
        ChatMessage typingMsg = new ChatMessage(TYPING_MESSAGE, ChatMessage.SENDER_AI);
        messages.add(typingMsg);
        requireActivity().runOnUiThread(() -> {
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerChat.scrollToPosition(messages.size() - 1);
        });
        String payloadText = userMessage;
        if (payloadText.length() < 6) {
            payloadText = "Please answer briefly and clearly: " + payloadText;
        }

        callGeminiAPIInternal(payloadText, /*retried*/ false);
    }

    private void callGeminiAPIInternal(String prompt, boolean retried) {
        MediaType mediaType = MediaType.parse("application/json");
        JSONObject requestBody = new JSONObject();
        try {
            JSONArray parts = new JSONArray().put(new JSONObject().put("text", prompt));
            JSONObject contentObj = new JSONObject().put("parts", parts);
            requestBody.put("contents", new JSONArray().put(contentObj));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(mediaType, requestBody.toString());
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + BuildConfig.GEMINI_API_KEY)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                uiFailGemini("Connection error! Try again later");
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (!isAdded() || getActivity() == null) {
                        if (response.body() != null) response.body().close();
                        return;
                    }
                    final String responseBodyString = response.body() != null ? response.body().string() : null;
                    if (response.body() != null) response.body().close();

                    if (!response.isSuccessful() || responseBodyString == null) {
                        uiFailGemini("Invalid response from Gemini (Code: " + response.code() + ")");
                        return;
                    }

                    JSONObject jsonResponse = new JSONObject(responseBodyString);
                    JSONArray candidates = jsonResponse.optJSONArray("candidates");
                    if (candidates == null || candidates.length() == 0) {
                        if (!retried) {
                            callGeminiAPIInternal("Answer this clearly and concisely: " + prompt, true);
                            return;
                        }
                        uiFailGemini("Gemini returned no candidates (possibly blocked by safety).");
                        return;
                    }

                    JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                    JSONArray parts = content != null ? content.optJSONArray("parts") : null;
                    String rawReply = (parts != null && parts.length() > 0)
                            ? parts.getJSONObject(0).optString("text", "").trim() : "";

                    if (rawReply.isEmpty()) {
                        uiFailGemini("Gemini returned empty text.");
                        return;
                    }

                    String processedReply = postProcess(rawReply);
                    requireActivity().runOnUiThread(() -> {
                        removeTypingMessage();
                        ChatMessage botMsg = new ChatMessage(processedReply, ChatMessage.SENDER_AI);
                        messages.add(botMsg);
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        recyclerChat.scrollToPosition(messages.size() - 1);
                        ChatHistoryManager.saveChatHistory(getContext(), messages);
                        isSending = false;
                    });
                } catch (Exception ex) {
                    uiFailGemini("Error parsing Gemini JSON response");
                }
            }

            private void uiFailGemini(String msg) {
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

    // =============== OPENAI (Pro Mode) ===============
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
                        Toast.makeText(getContext(), "Connection error! Try again later", Toast.LENGTH_SHORT).show();
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
                        uiFailOpenAI("Invalid response from OpenAI (Code: " + response.code() + ")");
                        return;
                    }

                    JSONObject jsonResponse = new JSONObject(responseBodyString);
                    JSONArray choices = jsonResponse.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        uiFailOpenAI("OpenAI returned no choices.");
                        return;
                    }

                    JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                    if (message == null) {
                        uiFailOpenAI("OpenAI response missing message.");
                        return;
                    }

                    String aiReply = message.optString("content", "").trim();
                    if (aiReply.isEmpty()) {
                        uiFailOpenAI("OpenAI returned empty content.");
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
                        isSending = false;
                    });
                } catch (Exception ex) {
                    uiFailOpenAI("Error parsing OpenAI JSON response");
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
